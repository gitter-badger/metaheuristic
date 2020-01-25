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

package ai.metaheuristic.ai.launchpad.beans;

import ai.metaheuristic.ai.Enums;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "MH_BATCH")
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

    // This field contains a value from MH_COMPANY.UNIQUE_ID, !NOT! from ID field
    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @NotNull
    @Column(name = "ACCOUNT_ID")
    public Long accountId;

    @NotNull
    @Column(name = "PLAN_ID")
    public Long planId;

    @Column(name = "WORKBOOK_ID")
    public Long workbookId;

    @Column(name="CREATED_ON")
    private long createdOn;

    @Column(name = "EXEC_STATE")
    public int execState;

    @Column(name = "PARAMS")
    public String params;

    @Column(name = "IS_DELETED")
    public boolean deleted;

    public Batch(Long planId, Long workbookId, Enums.BatchExecState state, Long accountId, Long companyId ) {
        this.planId = planId;
        this.workbookId = workbookId;
        this.accountId = accountId;
        this.companyId = companyId;
        this.createdOn = System.currentTimeMillis();
        this.execState=state.code;
    }
}
