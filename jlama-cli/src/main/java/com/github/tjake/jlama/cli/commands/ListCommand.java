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
import picocli.CommandLine;

import java.io.File;
import java.util.List;

@CommandLine.Command(name = "list", description = "Lists local models", abbreviateSynopsis = true)
public class ListCommand extends JlamaCli {
    @CommandLine.Option(names = {
        "--model-cache" }, paramLabel = "ARG", description = "The local directory for all downloaded models (default: ${DEFAULT-VALUE})")
    protected File modelDirectory = new File(JlamaCli.DEFAULT_MODEL_DIRECTORY);

    @Override
    public void run() {
        if (!modelDirectory.exists()) {
            System.out.println("No models found in " + modelDirectory.getAbsolutePath());
            System.exit(0);
        }
        List<ModelId> models = listLocalModels(modelDirectory);
        if (models.isEmpty()) {
            System.out.println("No models found in " + modelDirectory.getAbsolutePath());
            System.exit(0);
        }
        int idx = 1;
        for (ModelId m : models) {
            System.out.println(idx++ + ": " + m.fullName());
        }
        System.out.println("\nYou can reference a model by its number in other commands (e.g. 'jlama chat 1').");
    }
}
