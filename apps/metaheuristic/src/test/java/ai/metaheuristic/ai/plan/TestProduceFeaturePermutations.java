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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.ai.launchpad.beans.ExperimentFeature;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentFeatureRepository;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.utils.holders.IntHolder;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProduceFeaturePermutations extends PreparingExperiment {

    @Autowired
    public ExperimentService experimentService;

    @Autowired
    public ExperimentFeatureRepository experimentFeatureRepository;

    @After
    public void after() {
        try {
            experimentFeatureRepository.deleteByExperimentId(experiment.getId());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Test
    public void testFeaturePermutation() {
        experimentService.produceFeaturePermutations(true, experiment.getId(), List.of("aaa", "bbb", "ccc"), new IntHolder());
        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        assertNotNull(features);
        assertEquals(7, features.size());

        int i=0;
    }
}