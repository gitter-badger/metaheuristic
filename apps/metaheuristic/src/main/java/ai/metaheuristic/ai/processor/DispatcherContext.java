/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.processor;

import lombok.Data;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 12:45 AM
 */
@Data
public class DispatcherContext {

    // chunkSize must be inited with value from Dispatcher. Until then Processor will wait for initializing
    public Long chunkSize;

    public Integer maxVersionOfProcessor;
}
