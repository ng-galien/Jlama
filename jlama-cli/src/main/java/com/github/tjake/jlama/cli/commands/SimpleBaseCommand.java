/*
 * Copyright 2024 T Jake Luciani
 *
 * The Jlama Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.tjake.jlama.cli.commands;

import com.github.tjake.jlama.cli.JlamaCli;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.util.ProgressReporter;
import com.google.common.util.concurrent.Uninterruptibles;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import picocli.CommandLine;

public class SimpleBaseCommand extends JlamaCli {
    static AtomicReference<ProgressBar> progressRef = new AtomicReference<>();

    @CommandLine.ArgGroup(exclusive = false, heading = "Download Options:%n")
    protected DownloadSection downloadSection = new DownloadSection();

    @CommandLine.Option(names = {
        "--model-cache" }, paramLabel = "ARG", description = "The local directory for downloaded models (default: ${DEFAULT-VALUE})")
    protected File modelDirectory = new File(JlamaCli.DEFAULT_MODEL_DIRECTORY);

    @CommandLine.Parameters(index = "0", arity = "1", paramLabel = "<model name|index>", description = "The huggingface model owner/name pair or numeric index from 'jlama list'")
    protected String modelName;

    static class DownloadSection {
        @CommandLine.Option(names = {
            "--auto-download" }, paramLabel = "ARG", description = "Download the model if missing (default: ${DEFAULT-VALUE})", defaultValue = "false")
        Boolean autoDownload = false;

        @CommandLine.Option(names = {
            "--branch" }, paramLabel = "ARG", description = "The model branch to download from (default: ${DEFAULT-VALUE})", defaultValue = "main")
        String branch = "main";

        @CommandLine.Option(names = { "--auth-token" }, paramLabel = "ARG", description = "HuggingFace auth token (for restricted models)")
        String authToken = null;
    }

    // Validation d'un nom owner/name
    private static String[] parseModelName(String modelName) {
        if (modelName == null) {
            System.err.println("Model name must be in the form owner/name (was null)");
            System.exit(1);
        }
        int slash = modelName.indexOf('/');
        if (slash <= 0 || slash == modelName.length() - 1 || modelName.indexOf('/', slash + 1) != -1) {
            System.err.println("Model name must be in the form owner/name");
            System.exit(1);
        }
        return new String[]{modelName.substring(0, slash), modelName.substring(slash + 1)};
    }

    static String getOwner(String modelName) { return parseModelName(modelName)[0]; }
    static String getName(String modelName) { return parseModelName(modelName)[1]; }

    // Wrapper public pour compatibilité vers nouvelle résolution centrale
    public static String resolveModelName(String input, File modelDirectory) {
        ModelId id = JlamaCli.resolveModelName(input, () -> JlamaCli.listLocalModels(modelDirectory));
        return id.fullName();
    }

    // Overload conservant l'ancienne signature
    static void downloadModel(String owner, String name, File modelDirectory, String branch, String authToken, boolean downloadWeights) {
        downloadModel(new ModelId(owner, name), modelDirectory, branch, authToken, downloadWeights);
    }

    static void downloadModel(ModelId modelId, File modelDirectory, String branch, String authToken, boolean downloadWeights) {
        try {
            SafeTensorSupport.maybeDownloadModel(
                modelDirectory.getAbsolutePath(),
                Optional.of(modelId.owner()),
                modelId.name(),
                downloadWeights,
                Optional.ofNullable(URLEncoder.encode(branch, "UTF-8")),
                Optional.ofNullable(authToken),
                getProgressConsumer()
            );
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static Optional<ProgressReporter> getProgressConsumer() {
        if (System.console() == null) return Optional.empty();

        return Optional.of((ProgressReporter) (filename, sizeDownloaded, totalSize) -> {
            if (progressRef.get() == null || !progressRef.get().getTaskName().equals(filename)) {
                ProgressBarBuilder builder = new ProgressBarBuilder().setTaskName(filename)
                    .setInitialMax(totalSize)
                    .setStyle(ProgressBarStyle.ASCII);

                if (totalSize > 1000000) {
                    builder.setUnit("MB", 1000000);
                } else if (totalSize > 1000) {
                    builder.setUnit("KB", 1000);
                } else {
                    builder.setUnit("B", 1);
                }

                progressRef.set(builder.build());
            }

            progressRef.get().stepTo(sizeDownloaded);
            Uninterruptibles.sleepUninterruptibly(150, TimeUnit.MILLISECONDS);
        });
    }

    static Path getModel(String modelName, File modelDirectory, boolean autoDownload, String branch, String authToken) {
        return getModel(modelName, modelDirectory, autoDownload, branch, authToken, true);
    }

    static Path getModel(
        String modelName,
        File modelDirectory,
        boolean autoDownload,
        String branch,
        String authToken,
        boolean downloadWeights
    ) {
        ModelId mn = JlamaCli.resolveModelName(modelName, () -> JlamaCli.listLocalModels(modelDirectory));
        Path modelPath = SafeTensorSupport.constructLocalModelPath(modelDirectory.getAbsolutePath(), mn.owner(), mn.name());

        if (autoDownload) {
            downloadModel(mn, modelDirectory, branch, authToken, downloadWeights);
        } else if (!modelPath.toFile().exists()) {
            System.err.println("Model not found: " + modelPath);
            System.err.println("Use --auto-download to download the model");
            System.exit(1);
        }
        return modelPath;
    }
}
