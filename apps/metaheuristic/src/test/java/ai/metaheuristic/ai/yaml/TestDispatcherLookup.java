/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestDispatcherLookup {

    @Test
    public void testParsingYaml() throws IOException {
        try (InputStream is = TestDispatcherLookup.class.getResourceAsStream("/yaml/dispatchers.yaml")) {
            DispatcherLookupParamsYaml ssc = DispatcherLookupParamsYamlUtils.to(is);

            assertEquals(2, ssc.dispatchers.size());

            assertEquals(new ProcessorAndCoreData.DispatcherServerUrl("http://localhost:8080"), ssc.dispatchers.get(0).getDispatcherUrl());
            assertEquals(Enums.DispatcherLookupType.direct, ssc.dispatchers.get(0).lookupType);
            assertNull(ssc.dispatchers.get(0).publicKey);
            assertFalse(ssc.dispatchers.get(0).signatureRequired);
            assertFalse(ssc.dispatchers.get(0).disabled);

            assertEquals(new ProcessorAndCoreData.DispatcherServerUrl("https://host"), ssc.dispatchers.get(1).getDispatcherUrl());
            assertEquals(Enums.DispatcherLookupType.registry, ssc.dispatchers.get(1).lookupType);
            assertEquals("some-public-key", ssc.dispatchers.get(1).publicKey);
            assertTrue(ssc.dispatchers.get(1).signatureRequired);
            assertTrue(ssc.dispatchers.get(1).disabled);
        }
    }

    @Test
    public void test() {
        DispatcherLookupParamsYaml ssc = new DispatcherLookupParamsYaml();

        DispatcherLookupParamsYaml.DispatcherLookup config = new DispatcherLookupParamsYaml.DispatcherLookup();
        config.url = "http://localhost:8080";
        config.signatureRequired = false;
        config.lookupType = Enums.DispatcherLookupType.direct;

        ssc.dispatchers.add(config);

        config = new DispatcherLookupParamsYaml.DispatcherLookup();
        config.url = "https://host";
        config.signatureRequired = true;
        config.publicKey = "some-public-key";
        config.lookupType = Enums.DispatcherLookupType.registry;

        ssc.dispatchers.add(config);

        String yaml = DispatcherLookupParamsYamlUtils.toString(ssc);
        System.out.println(yaml);

        DispatcherLookupParamsYaml ssc1 = DispatcherLookupParamsYamlUtils.to(yaml);

        assertEquals(ssc.dispatchers.size(), ssc1.dispatchers.size());

        assertEquals(ssc.dispatchers.get(0).getDispatcherUrl(), ssc1.dispatchers.get(0).getDispatcherUrl());
        assertEquals(ssc.dispatchers.get(0).publicKey, ssc1.dispatchers.get(0).publicKey);
        assertEquals(ssc.dispatchers.get(0).signatureRequired, ssc1.dispatchers.get(0).signatureRequired);
        assertEquals(ssc.dispatchers.get(0).lookupType, ssc1.dispatchers.get(0).lookupType);

        assertEquals(ssc.dispatchers.get(1).getDispatcherUrl(), ssc1.dispatchers.get(1).getDispatcherUrl());
        assertEquals(ssc.dispatchers.get(1).publicKey, ssc1.dispatchers.get(1).publicKey);
        assertEquals(ssc.dispatchers.get(1).signatureRequired, ssc1.dispatchers.get(1).signatureRequired);
        assertEquals(ssc.dispatchers.get(1).lookupType, ssc1.dispatchers.get(1).lookupType);

    }

}
