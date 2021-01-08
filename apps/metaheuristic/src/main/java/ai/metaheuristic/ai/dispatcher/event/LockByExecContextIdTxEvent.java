/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event;

import lombok.AllArgsConstructor;

/**
 * @author Serge
 * Date: 12/20/2020
 * Time: 2:36 AM
 */
@AllArgsConstructor
public class LockByExecContextIdTxEvent {
    public final Long execContextId;

    public LockByExecContextIdEvent to() {
        return new LockByExecContextIdEvent(execContextId);
    }
}
