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

package aiai.ai.resource;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestParseStorageUrl {

    @Test
    public void testParse() {

        ResourceUtils.DiskStorageUri storageUri = ResourceUtils.parseStorageUrl("disk://aaaa/*");
        assertEquals("aaaa", storageUri.envCode);
        assertEquals("*", storageUri.resourceCode);

        storageUri = ResourceUtils.parseStorageUrl("disk://bbb");
        assertEquals("bbb", storageUri.envCode);
        assertNull(storageUri.resourceCode);
    }
}
