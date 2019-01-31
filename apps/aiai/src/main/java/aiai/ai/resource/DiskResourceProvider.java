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

import aiai.ai.Consts;
import aiai.ai.core.ExecProcessService;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.station.EnvService;
import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.station.StationTaskService;
import aiai.ai.yaml.env.DiskStorage;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.metadata.Metadata;
import aiai.ai.yaml.station.StationTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class DiskResourceProvider implements ResourceProvider {

    private final EnvService envService;
    private final StationTaskService stationTaskService;

    public DiskResourceProvider(EnvService envService, StationTaskService stationTaskService) {
        this.envService = envService;
        this.stationTaskService = stationTaskService;
    }

    @Override
    public List<AssetFile> prepareDataFile(
            File taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            String resourceCode, String storageUrl) {
        DiskStorageUri storageUri = parseStorageUrl(storageUrl);
        EnvYaml env = envService.getEnvYaml();
        DiskStorage diskStorage = env.findDiskStorageByCode(storageUri.envCode);
        if (diskStorage==null) {
            throw new ResourceProviderException("#015.020 The disk storage wasn't found for code: " + storageUri.envCode);
        }
        File path = new File(diskStorage.path);
        if (!path.exists()) {
            throw new ResourceProviderException("#015.024 The path of disk storage doesn't exist: " + path.getAbsolutePath());
        }

        AssetFile assetFile;
        if (storageUri.resourceCode.endsWith("*")) {
            assetFile = new AssetFile();
            assetFile.setFile( new File(path, storageUri.resourceCode) );
            assetFile.setProvided(true);
            assetFile.setContent(true);
            assetFile.setError(false);
            assetFile.setExist(true);
            assetFile.setFileLength(0);
        }
        else {
            assetFile = ResourceUtils.prepareAssetFile(path, null, storageUri.resourceCode);
        }

        return Collections.singletonList(assetFile);
    }

    @Override
    public ExecProcessService.Result processResultingFile(
            LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, Metadata.LaunchpadInfo launchpadCode,
            File outputResourceFile
    ) {
        if (outputResourceFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputResourceFile.getPath());
            stationTaskService.setResourceUploadedAndCompleted(launchpad.launchpadLookup.url, task.taskId);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputResourceFile.getPath();
            log.error(es);
            return new ExecProcessService.Result(false, -1, es);
        }
        return null;    }

    @Override
    public File getOutputResourceFile(
            String taskDir, LaunchpadLookupExtendedService.LaunchpadLookupExtended launchpad,
            StationTask task, String outputResourceCode, String storageUrl) {

        DiskStorageUri storageUri = parseStorageUrl(storageUrl);

        EnvYaml env = envService.getEnvYaml();
        DiskStorage diskStorage = env.findDiskStorageByCode(storageUri.envCode);
        if (diskStorage==null) {
            throw new ResourceProviderException("#015.020 The disk storage wasn't found for code: " + storageUri.envCode);
        }
        File path = new File(diskStorage.path);
        if (!path.exists()) {
            throw new ResourceProviderException("#015.024 The path of disk storage doesn't exist: " + path.getAbsolutePath());
        }

        return new File(path, outputResourceCode);
    }

    @Data
    @AllArgsConstructor
    public static class DiskStorageUri {
        public String envCode;
        public String resourceCode;
    }

    static DiskStorageUri parseStorageUrl(String storageUrl) {
        if (!storageUrl.startsWith(Consts.DISK_STORAGE_URL)) {
            throw new ResourceProviderException("#015.01 Wrong storageUrl format: " + storageUrl);
        }
        String uri = storageUrl.substring(Consts.DISK_STORAGE_URL.length());
        int idx = uri.indexOf('/');
        if (idx!=-1) {
            if (uri.indexOf('/') != uri.lastIndexOf('/')) {
                throw new ResourceProviderException("#015.05 Wrong storageUrl format: " + storageUrl);
            }
            return new DiskStorageUri(uri.substring(0, uri.indexOf('/')), uri.substring(uri.indexOf('/') + 1));
        }
        else {
            return new DiskStorageUri(uri, null);
        }
    }
}
