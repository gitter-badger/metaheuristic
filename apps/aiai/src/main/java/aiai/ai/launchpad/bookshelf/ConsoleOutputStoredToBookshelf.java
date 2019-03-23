/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.bookshelf;

import aiai.ai.launchpad.data.BaseDataClass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Collections;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ConsoleOutputStoredToBookshelf extends BaseDataClass {

    @Data
    @NoArgsConstructor
    public static class TaskOutput {
        public long taskId;
        public String console;
    }

    public ConsoleOutputStoredToBookshelf(String errorMessage) {
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public ConsoleOutputStoredToBookshelf(File dumpOfConsoleOutputs) {
        this.dumpOfConsoleOutputs = dumpOfConsoleOutputs;
    }

    public File dumpOfConsoleOutputs;
}