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

package aiai.ai.launchpad.rest.v1;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.flow.FlowTopLevelService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/launchpad/flow")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class FlowRestController {

    private final FlowTopLevelService flowTopLevelService;

    public FlowRestController(FlowTopLevelService flowTopLevelService) {
        this.flowTopLevelService = flowTopLevelService;
    }

    // ============= Flow =============

    @GetMapping("/flows")
    public FlowData.FlowsResult flows(@PageableDefault(size = 5) Pageable pageable) {
        return flowTopLevelService.getFlows(pageable);
    }

    @GetMapping(value = "/flow/{id}")
    public FlowData.FlowResult edit(@PathVariable Long id) {
        return flowTopLevelService.getFlow(id);
    }

    @GetMapping(value = "/flow-validate/{id}")
    public FlowData.FlowResult validate(@PathVariable Long id) {
        return flowTopLevelService.validateFlow(id);
    }

    @PostMapping("/flow-add-commit")
    public FlowData.FlowResult addFormCommit(@RequestBody Flow flow) {
        return flowTopLevelService.addFlow(flow);
    }

    @PostMapping("/flow-edit-commit")
    public FlowData.FlowResult editFormCommit(@RequestBody Flow flow) {
        return flowTopLevelService.updateFlow(flow);
    }

    @PostMapping("/flow-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return flowTopLevelService.deleteFlowById(id);
    }

    // ============= Flow instances =============

    @GetMapping("/flow-instances/{id}")
    public FlowData.FlowInstancesResult flowInstances(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        return flowTopLevelService.getFlowInstancesOrderByCreatedOnDesc(id, pageable);
    }

    @PostMapping("/flow-instance-add-commit")
    public FlowData.FlowInstanceResult flowInstanceAddCommit(Long flowId, String poolCode, String inputResourceParams) {
        //noinspection UnnecessaryLocalVariable
        FlowData.FlowInstanceResult flowInstanceResult = flowTopLevelService.addFlowInstance(flowId, poolCode, inputResourceParams);
        return flowInstanceResult;
    }

    @PostMapping("/flow-instance-create")
    public FlowData.TaskProducingResult createFlowInstance(Long flowId, String inputResourceParam) {
        return flowTopLevelService.createFlowInstance(flowId, inputResourceParam);
    }

    @GetMapping(value = "/flow-instance/{flowId}/{flowInstanceId}")
    public FlowData.FlowInstanceResult flowInstanceEdit(@SuppressWarnings("unused") @PathVariable Long flowId, @PathVariable Long flowInstanceId) {
        return flowTopLevelService.getFlowInstanceExtended(flowInstanceId);
    }

    @PostMapping("/flow-instance-delete-commit")
    public OperationStatusRest flowInstanceDeleteCommit(Long flowId, Long flowInstanceId) {
        return flowTopLevelService.deleteFlowInstanceById(flowId, flowInstanceId);
    }

    @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
    public OperationStatusRest flowInstanceTargetExecState(@SuppressWarnings("unused") @PathVariable Long flowId, @PathVariable String state, @PathVariable Long id) {
        return flowTopLevelService.changeFlowInstanceExecState(state, id);
    }

    // ============= Service methods =============

    @GetMapping(value = "/emulate-producing-tasks/{flowInstanceId}")
    public FlowData.TaskProducingResult emulateProducingTasks(@PathVariable Long flowInstanceId) {
        return flowTopLevelService.produceTasksWithoutPersistence(flowInstanceId);
    }

    @GetMapping(value = "/create-all-tasks")
    public void createAllTasks() {
        flowTopLevelService.createAllTasks();
    }

    @GetMapping(value = "/change-valid-status/{flowInstanceId}/{status}")
    public OperationStatusRest changeValidStatus(@PathVariable Long flowInstanceId, @PathVariable boolean status) {
        return flowTopLevelService.changeValidStatus(flowInstanceId, status);
    }



}