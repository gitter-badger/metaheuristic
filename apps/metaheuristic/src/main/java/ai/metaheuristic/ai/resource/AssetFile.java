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
package ai.metaheuristic.ai.resource;

import lombok.Data;

import java.io.File;

@Data
public class AssetFile {
    public File file;
    public long fileLength;
    public boolean isError;
    public boolean isContent;
    public boolean isExist;
    public boolean provided = false;

    @Override
    public String toString() {
        return "AssetFile{" +
                "file=" + (file!=null ? file.getPath()  : "null") +
                ", fileLength=" + fileLength +
                ", isError=" + isError +
                ", isContent=" + isContent +
                ", isExist=" + isExist +
                ", isProvided=" + provided +
                '}';
    }
}
