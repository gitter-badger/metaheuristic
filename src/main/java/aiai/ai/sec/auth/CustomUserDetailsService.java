package aiai.ai.sec.auth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 12.08.13
 * Time: 23:17
 */
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // pass   - 123
        // bcrypt - $2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu



        if (StringUtils.equals("qqq", username)) {
            List<GrantedAuthority> authList = new ArrayList<>();
            authList.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

            long userID = 0L;
            boolean accountNonExpired = true;
            boolean accountNonLocked = true;
            boolean credentialsNonExpired = true;
            boolean enabled = true;
            String password = "$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu";

            CustomUserDetails customUserDetails = new CustomUserDetails(userID, authList, username, password, accountNonExpired, accountNonLocked, credentialsNonExpired, enabled);
            return customUserDetails;
        }

        throw new UsernameNotFoundException("Username not found");

/*
        if (user == null) {
            throw new UsernameNotFoundException("Username not found");
        }
        else {
            List<GrantedAuthority> authList = new ArrayList<>();
            authList.add(new SimpleGrantedAuthority(user.getRole()));

            long userID = user.getId();
            boolean accountNonExpired = true;
            boolean accountNonLocked = true;
            boolean credentialsNonExpired = true;
            boolean enabled = true;
            String password = user.getPassword();

            CustomUserDetails customUserDetails = new CustomUserDetails(userID, authList, username, password, accountNonExpired, accountNonLocked, credentialsNonExpired, enabled);
            return customUserDetails;
        }
*/
    }

}
