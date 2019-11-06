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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Globals;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * User: Serg
 * Date: 11.06.2017
 * Time: 15:12
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class MultiHttpSecurityConfig {

    public final Globals globals;

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // https://github.com/spring-guides/gs-rest-service-cors/blob/master/complete/src/test/java/hello/GreetingIntegrationTests.java
    // TODO 2019-10-13 need to investigate how to use CORS with restTemplate
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of( globals.allowedOrigins ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(List.of("x-auth-token", "Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Configuration
    @Order(1)
    @RequiredArgsConstructor
    public static class RestAuthSecurityConfig extends WebSecurityConfigurerAdapter {

        private static final String REST_REALM = "REST realm";

        private final Globals globals;

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                    .antMatcher("/rest/**/**").cors()
                    .and()
                    .antMatcher("/rest/**/**").sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .authorizeRequests()
                    .antMatchers("/rest/login").permitAll()
                    .antMatchers("/rest/**/**").authenticated()
                    .and()
                    .antMatcher("/rest/**/**").httpBasic().realmName(REST_REALM)
                    .and()
                    .antMatcher("/rest/**/**").csrf().disable().headers().cacheControl();

            if (globals.isSslRequired) {
                http.requiresChannel().antMatchers("/**").requiresSecure();
            }
        }
    }

    @Configuration
    @Order
    @RequiredArgsConstructor
    public static class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

        private final CsrfTokenRepository csrfTokenRepository;
        private final Globals globals;

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                    .csrf().csrfTokenRepository(csrfTokenRepository)
                    .and()
                    .headers().frameOptions().sameOrigin()
                    .and()
                    .authorizeRequests()
                    .antMatchers("/manager/html").denyAll()
                    .antMatchers("/static/**/**", "/css/**", "/js/**", "/webjars/**").permitAll()
                    .antMatchers("/favicon.ico", "/", "/index", "/about", "/login", "/jssc", "/error/**").permitAll()
                    .antMatchers("/login").anonymous()
                    .antMatchers("/logout", "/launchpad/**/**").authenticated()
                    .antMatchers("/admin/**").hasAnyRole("ADMIN")
                    .antMatchers("/user/**").hasAnyRole("USER")
                    .antMatchers("/**/**").denyAll()
                    .and()
                    .formLogin()
                    .loginPage("/login")
                    .usernameParameter("j_username")
                    .passwordParameter("j_password")
                    .loginProcessingUrl("/jssc")
                    .defaultSuccessUrl("/index")
                    .and()
                    .logout()
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/index");

            if (globals.isSslRequired) {
                http.requiresChannel().antMatchers("/**").requiresSecure();
            }
        }
    }
}