/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
package ai.metaheuristic.ai.commands;

import ai.metaheuristic.ai.comm.Command;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import org.junit.Assert;
import org.junit.Test;

public class TestCommands {

    private static Command getCommandInstance(Command.Type type) {
        switch (type) {
            case Nop:
                return new Protocol.Nop();
            case ReportStation:
                return new Protocol.ReportStation();
            case RequestStationId:
                return new Protocol.RequestStationId();
            case AssignedStationId:
                return new Protocol.AssignedStationId();
            case ReAssignStationId:
                return new Protocol.ReAssignStationId();
            case RequestTask:
                return new Protocol.RequestTask();
            case AssignedTask:
                return new Protocol.AssignedTask();
            case ReportStationStatus:
                return new Protocol.ReportStationStatus();
            case ReportTaskProcessingResult:
                return new Protocol.ReportTaskProcessingResult();
            case ReportResultDelivering:
                return new Protocol.ReportResultDelivering();
            case WorkbookStatus:
                return new Protocol.WorkbookStatus();
            case StationTaskStatus:
                return new Protocol.StationTaskStatus();
            case CheckForMissingOutputResources:
                return new Protocol.CheckForMissingOutputResources();
            case ResendTaskOutputResource:
                return new Protocol.ResendTaskOutputResource();
            case ResendTaskOutputResourceResult:
                return new Protocol.ResendTaskOutputResourceResult();
            default:
                // if you get this exception, you'll have to add related enum to this switch block
                throw new IllegalStateException("unknown command type: " + type);
        }
    }

    @Test
    public void testCommandsIntegrity() {
        final ExchangeData data = new ExchangeData();
        for (Command.Type value : Command.Type.values()) {
            data.setCommand(getCommandInstance(value));
        }
        Assert.assertEquals(Command.Type.values().length, data.getCommands().size());
    }

}