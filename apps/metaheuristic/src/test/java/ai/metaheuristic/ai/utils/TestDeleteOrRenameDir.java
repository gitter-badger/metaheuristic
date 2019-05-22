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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.station.StationTaskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
public class TestDeleteOrRenameDir {

    private static Random r = new Random();

    @Test
    public void testDeleteOrRenameDir() throws IOException {
        String tempDirName = System.getProperty("java.io.tmpdir");

        File tempDir = new File(tempDirName);
        log.info("tempDir: {}", tempDir.getPath());
        assertTrue(tempDir.exists());
        assertTrue(tempDir.isDirectory());

        File d = new File(tempDir,"temp-dir-"+r.nextInt(100)+System.nanoTime());
        log.info("d: {}", d.getPath());
        assertFalse(d.exists());
        d.mkdirs();
        assertTrue(d.exists());
        assertTrue(d.isDirectory());


        File f = new File(d, Consts.TASK_YAML);
        log.info("f: {}", f.getPath());
        f.createNewFile();
        assertTrue(f.exists());
        assertTrue(f.isFile());

        boolean status = StationTaskService.deleteOrRenameTaskDir(d, f);
        assertTrue(status);
        assertFalse(d.exists());
    }

}