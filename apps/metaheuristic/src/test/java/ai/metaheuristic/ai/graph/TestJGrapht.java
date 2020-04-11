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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 4/10/2020
 * Time: 5:12 PM
 */
public class TestJGrapht {

    @Data
    @EqualsAndHashCode(of = "itemId")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        public Long id;
        public Long itemId;
        public String code;

        public Item(long id) {
            this.id = id;
        }
    }

    @Test
    public void test() {
        DirectedAcyclicGraph<Item, DefaultEdge> graph = new DirectedAcyclicGraph<>(
                Item::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false, true);

        AtomicLong id = new AtomicLong();
        graph.setVertexSupplier(()->new Item(id.incrementAndGet()));

        final Item v1001 = graph.addVertex();
        assertNotNull(v1001.id);
        v1001.itemId = 1001L;
        v1001.code = "code-1001";

        Item item = graph.vertexSet()
                .stream()
                .filter(o -> o.itemId.equals(1001L))
                .findAny().orElse(null);
        assertNotNull(item);
        assertEquals(v1001.id, item.id);

        final Item v1002 = graph.addVertex();
        assertNotNull(v1002.id);
        v1002.itemId = 1002L;
        v1002.code = "code-1002";

        item = graph.vertexSet()
                .stream()
                .filter(o -> o.itemId.equals(1002L))
                .findAny().orElse(null);
        assertNotNull(item);
        assertEquals(v1002.id, item.id);

        graph.addEdge(v1001, v1002);
    }

}
