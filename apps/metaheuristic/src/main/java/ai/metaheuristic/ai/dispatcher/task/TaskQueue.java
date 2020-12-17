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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 12/16/2020
 * Time: 3:09 AM
 */
public class TaskQueue {

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"taskId"})
    public static class QueuedTask {
        public Long execContextId;
        public Long taskId;
        public TaskImpl task;
        public TaskParamsYaml taskParamYaml;
        public String tags;
        public int priority;
    }

    public static class AllocatedTask {
        public final QueuedTask queuedTask;
        public boolean assigned;

        public AllocatedTask(QueuedTask queuedTask) {
            this.queuedTask = queuedTask;
        }
    }

    private static final int GROUP_SIZE = 5;
    private static final int MIN_QUEUE_SIZE_DEFAULT = 25;

    @Slf4j
    public static class TaskGroup {
        @Nullable
        public Long execContextId;

        public final AllocatedTask[] tasks = new AllocatedTask[GROUP_SIZE];
        public int allocated = 0;
        public int priority;

        public TaskGroup(Long execContextId, int priority) {
            this.execContextId = execContextId;
            this.priority = priority;
        }

        public boolean alreadyRegistered(Long taskId) {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task!=null && task.queuedTask.taskId.equals(taskId)) {
                    return true;
                }
            }
            return false;
        }

        public boolean deRegisterTask(Long taskId) {
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]!=null && tasks[i].queuedTask.taskId.equals(taskId)) {
                    tasks[i] = null;
                    --allocated;
                    if (Arrays.stream(tasks).noneMatch(Objects::nonNull)) {
                        execContextId = null;
                        priority = 0;
                    }
                    return true;
                }
            }
            return false;
        }

        public boolean isNewTask() {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task != null && !task.assigned) {
                    return true;
                }
            }
            return false;
        }

        public void addTask(QueuedTask task) {
            if (noneTasks()) {
                priority = task.priority;
            }
            if (priority!= task.priority) {
                throw new IllegalStateException(
                        S.f("Different priority, group priority: %d, task priority %d",
                                priority, task.priority));
            }
            if (execContextId!=null && !execContextId.equals(task.execContextId)) {
                throw new IllegalStateException("wrong execContextId");
            }
            if (allocated==GROUP_SIZE) {
                throw new IllegalStateException("already allocated");
            }
            if (execContextId==null) {
                execContextId = task.execContextId;
            }
            ++allocated;
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]==null) {
                    tasks[i] = new AllocatedTask(task);
                    break;
                }
            }
        }

        public void assignTask(Long taskId) {
            for (AllocatedTask task : tasks) {
                if (task != null && task.queuedTask.taskId.equals(taskId)) {
                    task.assigned = true;
                    break;
                }
            }
        }

        public void reset() {
            allocated = 0;
            execContextId = null;
            Arrays.fill(tasks, null);
        }

        public boolean isEmpty() {
            boolean noneTasks = noneTasks();
            if (!noneTasks && execContextId==null) {
                log.warn("There is a task but execContextId is null. Shouldn't happened.");
            }
            return execContextId==null || noneTasks;
        }

        public boolean noneTasks() {
            return Arrays.stream(tasks).noneMatch(Objects::nonNull);
        }
    }

    public static class GroupIterator implements Iterator<AllocatedTask> {

        private int groupPtr = 0;
        private int taskPtr = 0;
        private final CopyOnWriteArrayList<TaskGroup> taskGroups;

        public GroupIterator(CopyOnWriteArrayList<TaskGroup> taskGroups) {
            this.taskGroups = taskGroups;
        }

        @Override
        public boolean hasNext() {
            int idx = taskPtr;
            for (int i = groupPtr; i < taskGroups.size(); i++) {
                TaskGroup taskGroup = taskGroups.get(i);
                for (int j = idx; j < GROUP_SIZE; j++) {
                    AllocatedTask task = taskGroup.tasks[j];
                    if (task!=null && !task.assigned) {
                        return true;
                    }
                }
                idx = 0;
            }
            return false;
        }

        @Override
        public AllocatedTask next() {
            for (; groupPtr < taskGroups.size(); groupPtr++) {
                TaskGroup taskGroup = taskGroups.get(groupPtr);
                for (; taskPtr < GROUP_SIZE; taskPtr++) {
                    AllocatedTask task = taskGroup.tasks[taskPtr];
                    if (task!=null && !task.assigned) {
                        ++taskPtr;
                        if (taskPtr==GROUP_SIZE) {
                            taskPtr = 0;
                            ++groupPtr;
                        }
                        return task;
                    }
                }
                taskPtr = 0;
            }
            throw new NoSuchElementException();
        }

    }

    public GroupIterator getIterator() {
        return new GroupIterator(taskGroups);
    }

    private final int minQueueSize;
    private final CopyOnWriteArrayList<TaskGroup> taskGroups = new CopyOnWriteArrayList<>();

    public TaskQueue() {
        this(MIN_QUEUE_SIZE_DEFAULT);
    }

    public TaskQueue(int minQueueSize) {
        this.minQueueSize = minQueueSize;
    }

    public void removeAll(List<QueuedTask> forRemoving) {
        if (forRemoving.isEmpty()) {
            return;
        }
        List<Long> execContextIds = forRemoving.stream().map(o->o.execContextId).collect(Collectors.toList());
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextIds.contains(taskGroup.execContextId)) {
                for (QueuedTask queuedTask : forRemoving) {
                    if (queuedTask.execContextId.equals(taskGroup.execContextId)) {
                        taskGroup.deRegisterTask(queuedTask.taskId);
                    }
                }
            }
        }
        shrink();
    }

    public void addNewTask(QueuedTask task) {
        List<TaskGroup> temp = new ArrayList<>(taskGroups);
        temp.sort(Comparator.comparingInt(o -> o.allocated));
        TaskGroup taskGroup = null;
        for (int i = 0; i < taskGroups.size(); i++) {
            TaskGroup group = taskGroups.get(i);
            if (group.priority < task.priority) {
                taskGroup = new TaskGroup(task.execContextId, task.priority);
                taskGroups.add(i, taskGroup);
                break;
            }

            if (task.execContextId.equals(group.execContextId) && task.priority==group.priority) {
                if (group.allocated<GROUP_SIZE) {
                    taskGroup = group;
                }
            }
            else if (group.execContextId==null) {
                taskGroup = group;
                taskGroup.priority = task.priority;
            }

        }

        if (taskGroup==null) {
            taskGroup = new TaskGroup(task.execContextId, task.priority);
            taskGroups.add(taskGroup);
        }
        taskGroup.addTask(task);
    }

    public void shrink() {

        if (taskGroups.size()>minQueueSize) {
            int size = taskGroups.size();
            for (int i = 0; i < size; i++) {
                if (size==minQueueSize) {
                    break;
                }
                if (taskGroups.get(i).noneTasks()) {
                    taskGroups.remove(i);
                    --size;
                    --i;
                }
            }
        }
    }

    public void deleteByExecContextId(Long execContextId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                taskGroup.reset();
            }
        }
        shrink();
    }

    public boolean alreadyRegistered(Long taskId) {
        return taskGroups.stream().anyMatch(o->o.alreadyRegistered(taskId));
    }

    public void deRegisterTask(Long execContextId, Long taskId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                if (taskGroup.deRegisterTask(taskId)) {
                    return;
                }
            }
        }
    }

    public boolean isQueueEmpty() {
        return taskGroups.stream().noneMatch(TaskGroup::isNewTask);
    }

    public int groupCount() {
        return taskGroups.size();
    }
}