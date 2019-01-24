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

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;

@Controller
@RequestMapping("/launchpad")
@Slf4j
@Profile("launchpad")
public class SnippetController {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final BinaryDataService binaryDataService;

    @Data
    public static class Result {
        Iterable<Snippet> snippets;
    }

    public SnippetController(Globals globals, SnippetRepository snippetRepository, SnippetCache snippetCache, SnippetService snippetService, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.snippetCache = snippetCache;
        this.snippetService = snippetService;
        this.binaryDataService = binaryDataService;
    }

    @GetMapping("/snippets")
    public String init(@ModelAttribute Result result,
                       @ModelAttribute("errorMessage") final String errorMessage,
                       @ModelAttribute("infoMessages") final String infoMessages) {
        result.snippets = snippetRepository.findAll();
        return "launchpad/snippets";
    }

    @GetMapping("/snippet-delete/{id}")
    public HttpEntity<String> delete(@ModelAttribute Result result, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        log.info("Start deleting snippet with id: {}", id );
        final Snippet snippet = snippetCache.findById(id);
        if (snippet != null) {
            snippetCache.delete(snippet.getId());
            binaryDataService.deleteByCodeAndDataType(snippet.getSnippetCode(), Enums.BinaryDataType.SNIPPET);
        }
        return new HttpEntity<>("true");
    }

    @PostMapping(value = "/snippet-upload-from-file")
    public String uploadSnippet(final MultipartFile file, final RedirectAttributes redirectAttributes) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.01 name of uploaded file is null");
            return "redirect:/launchpad/snippets";
        }
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.02 '.' wasn't found, bad filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!".zip".equals(ext)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#422.03 only '.zip' files is supported, filename: " + originFilename);
            return "redirect:/launchpad/snippets";
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("snippet-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#422.04 can't create temporary directory in " + location);
                return "redirect:/launchpad/snippets";
            }
            final File zipFile = new File(tempDir, "snippet.zip");
            log.debug("Start storing an uploaded snippet to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start unzipping archive");
            ZipUtils.unzipFolder(zipFile, tempDir);
            log.debug("Start loading snippet data to db");
            snippetService.loadSnippetsRecursively(tempDir);
        }
        catch (Exception e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#422.05 can't load snippets, Error: " + e.toString());
            return "redirect:/launchpad/snippets";
        }

        log.debug("All done. Send redirect");
        return "redirect:/launchpad/snippets";
    }
}
