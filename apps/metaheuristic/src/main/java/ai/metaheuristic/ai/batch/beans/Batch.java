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

package ai.metaheuristic.ai.batch.beans;

import ai.metaheuristic.ai.Enums;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "PILOT_BATCH")
@Data
@NoArgsConstructor
@ToString(exclude = "params")
public class Batch implements Serializable {
    private static final long serialVersionUID = -3509391644278818781L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "PLAN_ID")
    public Long planId;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name = "EXEC_STATE")
    public int execState;

    @Column(name = "PARAMS")
    public String params;

    public Batch(Long planId, Enums.BatchExecState state) {
        this.planId = planId;
        this.createdOn = System.currentTimeMillis();
        this.execState=state.code;
    }
}