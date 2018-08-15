/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.comm;

import aiai.ai.beans.InviteResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 22:20
 */
public class Protocol {

    /**
     * stub command, which is actually doing nothing
     */
    @EqualsAndHashCode(callSuper = false)
    public static class Nop extends Command {
        public Nop() {
            this.setType(Type.Nop);
        }
    }

    public static class RequestStationId extends Command {
        public RequestStationId() {
            this.setType(Type.RequestStationId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class AssignedExperimentSequence extends Command {

        @Data
        public static class SimpleSequence {
            String params;
            Long experimentSequenceId;
        }
        List<SimpleSequence> sequences;

        public AssignedExperimentSequence() {
            this.setType(Type.AssignedExperimentSequence);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class AssignedStationId extends Command {
        public String assignedStationId;

        public AssignedStationId(String assignedStationId) {
            this.setType(Type.AssignedStationId);
            this.assignedStationId = assignedStationId;
        }

        public AssignedStationId() {
            this.setType(Type.AssignedStationId);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ReAssignStationId extends Command {
        String stationId;

        public ReAssignStationId(Long stationId) {
            this(Long.toString(stationId));
        }

        public ReAssignStationId(String stationId) {
            this.setType(Type.ReAssignStationId);
            this.stationId = stationId;
        }

        public ReAssignStationId() {
            this.setType(Type.ReAssignStationId);
        }
    }

    public static class ReportStation extends Command {
        public ReportStation() {
            this.setType(Type.ReportStation);
        }
    }

    public static class RequestExperimentSequence extends Command {
        public RequestExperimentSequence() {
            this.setType(Type.RequestExperimentSequence);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class RegisterInvite extends Command {
        private String invite;

        public RegisterInvite(String invite) {
            this.setType(Type.RegisterInvite);
            this.invite = invite;
        }

        public RegisterInvite() {
            this.setType(Type.RegisterInvite);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class RegisterInviteResult extends Command {
        private InviteResult inviteResult;

        public RegisterInviteResult() {
            this.setType(Type.RegisterInviteResult);
        }
    }


}
