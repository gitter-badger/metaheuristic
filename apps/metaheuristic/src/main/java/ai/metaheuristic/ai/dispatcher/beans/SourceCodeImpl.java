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
package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "MH_SOURCE_CODE")
@Data
@NoArgsConstructor
@ToString(exclude = "scspy")
public class SourceCodeImpl implements Serializable, SourceCode {
    private static final long serialVersionUID = 6764501814772365639L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
    public Long id;

    @Version
    public Integer version;

    @NotNull
    @Column(name = "COMPANY_ID")
    public Long companyId;

    @Column(name = "UID")
    public String uid;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "PARAMS")
    private String params;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.scspy =null;
        }
    }

    public String getParams() {
        return params;
    }

    @Column(name = "IS_LOCKED")
    public boolean locked;

    @Column(name = "IS_VALID")
    public boolean valid;

    @Transient
    @JsonIgnore
    private @Nullable SourceCodeStoredParamsYaml scspy = null;

    @JsonIgnore
    public SourceCodeStoredParamsYaml getSourceCodeStoredParamsYaml() {
        if (scspy ==null) {
            synchronized (this) {
                if (scspy ==null) {
                    //noinspection UnnecessaryLocalVariable
                    SourceCodeStoredParamsYaml temp = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.to(params);
                    scspy = temp;
                }
            }
        }
        return scspy;
    }

    @JsonIgnore
    public void updateParams(SourceCodeStoredParamsYaml scspy) {
        setParams(SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.toString(scspy));
    }
}