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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author Serge
 * Date: 10/28/2020
 * Time: 1:08 AM
 */
public class TestExecContextSorting {

    private static final String CTX_1 = "1";
    private static final String CTX_1_2___1 = "1,2###1";
    private static final String CTX_1_2___2 = "1,2###2";
    private static final String CTX_1_2___3 = "1,2###3";
    private static final String CTX_1_2___10 = "1,2###10";
    private static final String CTX_1_2___11 = "1,2###11";
    private static final String CTX_1_2___100 = "1,2###100";
    private static final String CTX_1_2___5 = "1,2###5";
    private static final String CTX_1_2___50 = "1,2###50";
    private static final String CTX_1_2___500 = "1,2###500";
    private static final String CTX_1_2 = "1,2";

    private static final List<String> ALL_CTXS = new ArrayList<>(
            List.of(CTX_1_2___2, CTX_1_2___1, CTX_1_2___3, CTX_1_2___10, CTX_1_2___11,
                    CTX_1_2___100, CTX_1_2___5, CTX_1_2___50, CTX_1_2___500, CTX_1, CTX_1_2
                    ));

    private static final List<String> EXPECTED = List.of(
            CTX_1, CTX_1_2, CTX_1_2___1, CTX_1_2___2, CTX_1_2___3, CTX_1_2___5,
            CTX_1_2___10, CTX_1_2___11, CTX_1_2___50,
            CTX_1_2___100, CTX_1_2___500
    );

    @Test
    public void test() {

        List<String> sorted = ALL_CTXS.stream()
                .sorted(ExecContextService::compare).collect(Collectors.toList());

        System.out.println(sorted);

        assertArrayEquals(EXPECTED.toArray(new String[0]), sorted.toArray(new String[0]));
    }

}
