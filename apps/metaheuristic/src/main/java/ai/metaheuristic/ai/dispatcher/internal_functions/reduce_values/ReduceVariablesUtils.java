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

package ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values;

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static ai.metaheuristic.ai.Consts.MH_METADATA_YAML_FILE_NAME;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:27 PM
 */
public class ReduceVariablesUtils {

    public static ReduceVariablesData.ReduceVariablesResult  reduceVariables(File zipFile, ReduceVariablesData.Config config) {

        ReduceVariablesData.VariablesData data = loadData(zipFile, config.fixName);

        ReduceVariablesData.ReduceVariablesResult result = new ReduceVariablesData.ReduceVariablesResult();


        return result;
    }

    @SneakyThrows
    public static ReduceVariablesData.VariablesData loadData(File zipFile, boolean fixVarName) {

        File tempDir = DirUtils.createMhTempDir("reduce-variables-");
        if (tempDir==null) {
            throw new RuntimeException("Can't create temp dir in metaheuristic-temp dir");
        }
        File zipDir = new File(tempDir, "zip");
        ZipUtils.unzipFolder(zipFile, zipDir);

        ReduceVariablesData.VariablesData data = new ReduceVariablesData.VariablesData();

        Collection<File> files =  FileUtils.listFiles(zipDir, new String[]{"zip"}, true);
        for (File f : files) {
            File tmp = DirUtils.createTempDir(tempDir, "load-data");
            File zipDataDir = new File(tmp, "zip");
            ZipUtils.unzipFolder(f, zipDataDir);

            File[] top = zipDataDir.listFiles(File::isDirectory);
            if (top==null || top.length==0) {
                throw new RuntimeException("can't find any dir in " + zipDataDir.getAbsolutePath());
            }

            File[] ctxDirs = top[0].listFiles(File::isDirectory);
            if (ctxDirs==null) {
                throw new RuntimeException("can't read content od dir " + top[0].getAbsolutePath());
            }

            ReduceVariablesData.TopPermutedVariables pvs = new ReduceVariablesData.TopPermutedVariables();
            data.permutedVariables.add(pvs);

            pvs.subPermutedVariables = new ArrayList<>();
            for (File ctxDir : ctxDirs) {
                File metadata = new File(ctxDir, MH_METADATA_YAML_FILE_NAME);
                MetadataAggregateFunctionParamsYaml mafpy;
                if (metadata.exists()) {
                    String yaml = FileUtils.readFileToString(metadata, StandardCharsets.UTF_8);
                    mafpy = MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
                }
                else {
                    mafpy = new MetadataAggregateFunctionParamsYaml();
                }

                Map<String, String> values = new HashMap<>();
                for (File file : FileUtils.listFiles(ctxDir, null, false)) {
                    final String fileName = file.getName();
                    if (MH_METADATA_YAML_FILE_NAME.equals(fileName)) {
                        continue;
                    }
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    String varName = mafpy.mapping.stream()
                            .filter(o->o.get(fileName)!=null)
                            .findFirst()
                            .map(o->o.get(fileName))
                            .orElse( fixVarName(fileName, fixVarName) );
                    values.put(varName, content);
                }

                if (ctxDir.getName().equals("1")) {
                    pvs.values.putAll(values);
                }
                else {
                    final ReduceVariablesData.PermutedVariables permutedVariables = new ReduceVariablesData.PermutedVariables();
                    permutedVariables.values.putAll(values);
                    pvs.subPermutedVariables.add(permutedVariables);
                }
            }

            int i=0;
        }

        return data;
    }

    private static String fixVarName(String name, boolean fix) {
        if (fix) {
            int idx = name.lastIndexOf('.');
            if (idx==-1) {
                return name;
            }
            return name.substring(0, idx);
        }
        return name;

    }
}
