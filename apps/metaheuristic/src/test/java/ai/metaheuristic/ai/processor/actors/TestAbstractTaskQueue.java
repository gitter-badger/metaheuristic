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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class TestAbstractTaskQueue {

    public static class SimpleClass extends AbstractTaskQueue<DownloadVariableTask> {
    }

    @Test
    public void test() {
        SimpleClass actor = new SimpleClass();

        DownloadVariableTask task = new DownloadVariableTask("resource-id-01", 10, new File("aaa"), null);

        actor.add(task);
        assertEquals(1, actor.queueSize());

        DownloadVariableTask task1 = new DownloadVariableTask("resource-id-01", 10, new File("bbb"), null);
        actor.add(task1);
        assertEquals(1, actor.queueSize());

        DownloadVariableTask t1 = actor.poll();
        assertNotNull(t1);

        DownloadVariableTask t2 = actor.poll();
        assertNull(t2);

        DownloadVariableTask task2 = new DownloadVariableTask("resource-id-02", 10, new File("."), null);
        actor.add(task1);
        actor.add(task2);
        assertEquals(2, actor.queueSize());

        // now it doesn't matter which task because resourceId is unique across all tasks
        DownloadVariableTask task3 = new DownloadVariableTask("resource-id-02", 11, new File("."), null);
        actor.add(task3);
        assertEquals(2, actor.queueSize());

    }
}
