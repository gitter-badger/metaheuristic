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
package aiai.ai.launchpad.snippet;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.repositories.ExperimentSnippetRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.utils.Checksum;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("launchpad")
public class SnippetService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final BinaryDataService binaryDataService;

    public SnippetService(Globals globals, SnippetRepository snippetRepository, SnippetCache snippetCache, ExperimentSnippetRepository experimentSnippetRepository, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.snippetCache = snippetCache;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.binaryDataService = binaryDataService;
    }

    @PostConstruct
    public void init() {
    }

    public List<ExperimentSnippet> getTaskSnippetsForExperiment(Long experimentId) {
        List<ExperimentSnippet> experimentSnippets = experimentSnippetRepository.findByExperimentId(experimentId);
        ExperimentUtils.sortExperimentSnippets(experimentSnippets);
        return experimentSnippets;
    }

    public List<Snippet> getSnippets(long experimentId){
        List<ExperimentSnippet> experimentSnippets = experimentSnippetRepository.findByExperimentId(experimentId);
        List<Snippet> snippets = new ArrayList<>();
        for (ExperimentSnippet experimentSnippet : experimentSnippets) {
            SnippetVersion version = SnippetVersion.from(experimentSnippet.getSnippetCode());
            if (version==null) {
                log.error("#295.01 wrong format of snippet code: {}", experimentSnippet.getSnippetCode());
                continue;
            }
            Snippet snippet = snippetRepository.findByNameAndSnippetVersion(version.name, version.version);
            if (snippet==null) {
                log.error("#295.07 Can't find snippet for code: {}", experimentSnippet.getSnippetCode());
                continue;
            }
            snippets.add(snippet);
        }
        return snippets;
    }

    public static void sortSnippetsByType(List<ExperimentSnippet> snippets) {
        snippets.sort(Comparator.comparing(ExperimentSnippet::getType));
    }

    public boolean hasFit(List<ExperimentSnippet> experimentSnippets) {
        if (experimentSnippets ==null || experimentSnippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : experimentSnippets) {
            if ("fit".equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPredict(List<ExperimentSnippet> experimentSnippets) {
        if (experimentSnippets ==null || experimentSnippets.isEmpty()) {
            return false;
        }
        for (ExperimentSnippet snippet : experimentSnippets) {
            if ("predict".equals(snippet.getType())) {
                return true;
            }
        }
        return false;
    }

    public interface SnippetFilter {
        boolean filter(Snippet snippet);
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<Snippet> snippets, List<SnippetCode> snippetCodes,
                                                     SnippetFilter snippetFilter) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (Snippet snippet : snippets) {
            boolean isExist=false;
            for (SnippetCode snippetCode : snippetCodes) {
                if (snippet.getSnippetCode().equals(snippetCode.getSnippetCode()) ) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (snippetFilter.filter(snippet)) {
                    continue;
                }
                selectOptions.add( new SimpleSelectOption(snippet.getSnippetCode(), String.format("Type: %s; Code: %s:%s", snippet.getType(), snippet.getName(), snippet.getSnippetVersion())));
            }
        }
        return selectOptions;
    }

    public List<ExperimentSnippet> getTaskSnippets(Iterable<Snippet> snippets, Experiment experiment) {
        List<ExperimentSnippet> experimentSnippets = new ArrayList<>();
        List<ExperimentSnippet> tss = getTaskSnippetsForExperiment(experiment.getId());
        for (Snippet snippet : snippets) {
            for (ExperimentSnippet experimentSnippet : tss) {
                if (snippet.getSnippetCode().equals(experimentSnippet.getSnippetCode()) ) {
                    // it should be ok without this line but just for sure
                    experimentSnippet.type = snippet.type;
                    experimentSnippets.add(experimentSnippet);
                    break;
                }
            }
            //noinspection unused
            int i=0;
        }
        return experimentSnippets;
    }

    void loadSnippetsRecursively(File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);
        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("Load snippets from {}", dir.getPath());
                loadSnippetsFromDir(dir);
                loadSnippetsRecursively(dir);
            }
        }
    }

    /**
     * load snippets from directory
     *
     * @param srcDir File
     */
    private void loadSnippetsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "snippets.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.11 File 'snippets.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return;
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        SnippetsConfig snippetsConfig = SnippetsConfigUtils.to(cfg);
        for (SnippetsConfig.SnippetConfig snippetConfig : snippetsConfig.snippets) {
            SnippetsConfig.SnippetConfigStatus status = snippetConfig.validate();
            if (!status.isOk) {
                log.error(status.error);
                continue;
            }
            String sum=null;
            File file = null;
            long length=0;
            if (globals.isSnippetChecksumRequired) {
                if (snippetConfig.fileProvided) {
                    sum = Checksum.Type.SHA256.getChecksum(new ByteArrayInputStream(snippetConfig.env.getBytes()));
                }
                else {
                    file = new File(srcDir, snippetConfig.file);
                    if (!file.exists()) {
                        throw new IllegalStateException("File " + snippetConfig.file + " wasn't found in " + srcDir.getAbsolutePath());
                    }
                    try (InputStream inputStream = new FileInputStream(file)) {
                        sum = Checksum.Type.SHA256.getChecksum(inputStream);
                    }
                    length = file.length();
                }
            }
            else {
                throw new NotImplementedException("Not implemented yet");
            }
            Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetConfig.name, snippetConfig.version);
            if (snippet!=null) {
                final String checksum = Checksum.fromJson(snippet.checksum).checksums.get(Checksum.Type.SHA256);
                if (!sum.equals(checksum)) {
                    if (globals.isReplaceSnapshot && snippetConfig.version.endsWith(Consts.SNAPSHOT_SUFFIX)) {
                        storeSnippet(snippetConfig, sum, file, length, snippet);
                    }
                    else {
                        log.warn("#295.14 Updating of snippets is prohibited, not a snapshot version '{}:{}'", snippet.name, snippet.snippetVersion);
                    }
                }
            }
            else {
                snippet = new Snippet();
                storeSnippet(snippetConfig, sum, file, length, snippet);
            }
        }
    }

    private void storeSnippet(SnippetsConfig.SnippetConfig snippetConfig, String sum, File file, long length, Snippet snippet) throws IOException {
        setChecksum(snippetConfig, sum, snippet);
        snippet.name = snippetConfig.name;
        snippet.snippetVersion = snippetConfig.version;
        snippet.type = snippetConfig.type;
        snippet.filename = snippetConfig.file;
        snippet.fileProvided = snippetConfig.fileProvided;
        snippet.length = length;
        snippet.env = snippetConfig.env;
        snippet.params = snippetConfig.params;
        snippet.reportMetrics = snippetConfig.isMetrics();
        snippetCache.save(snippet);
        if (file != null) {
            try (InputStream inputStream = new FileInputStream(file)) {
                String snippetCode = snippet.getSnippetCode();
                binaryDataService.save(inputStream, snippet.length, Enums.BinaryDataType.SNIPPET, snippetCode, snippetCode, false, null, null);
            }
        }
    }

    private void setChecksum(SnippetsConfig.SnippetConfig snippetConfig, String sum, Snippet snippet) {
        if (snippetConfig.checksums != null) {
            // already defined checksum in snippets.yaml
            Checksum checksum = new Checksum();
            checksum.checksums.putAll(snippetConfig.checksums);
            snippet.checksum = checksum.toJson();
            boolean isSigned = false;
            for (Map.Entry<Checksum.Type, String> entry : snippetConfig.checksums.entrySet()) {
                if (entry.getKey().isSign) {
                    isSigned = true;
                    break;
                }
            }
            snippet.setSigned(isSigned);
        } else {
            // set the new checksum
            snippet.checksum = new Checksum(Checksum.Type.SHA256, sum).toJson();
            snippet.setSigned(false);
        }
    }
}
