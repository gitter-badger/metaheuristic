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

package ai.metaheuristic.ai.launchpad.account;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/account")
@Profile("launchpad")
public class AccountController {

    private final AccountTopLevelService accountTopLevelService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Globals globals;

    public AccountController(AccountTopLevelService accountTopLevelService, AccountRepository accountRepository, PasswordEncoder passwordEncoder, Globals globals) {
        this.accountTopLevelService = accountTopLevelService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.globals = globals;
    }

    @GetMapping("/accounts")
    public String accounts(Model model,
                           @ModelAttribute("result") AccountData.AccountsResult result,
                           @PageableDefault(size = 5) Pageable pageable,
                           @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                           @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {

        AccountData.AccountsResult accounts = accountTopLevelService.getAccounts(pageable);
        ControllerUtils.addMessagesToModel(model, accounts);
        model.addAttribute("result", accounts);
        return "launchpad/account/accounts";
    }

    // for AJAX
    @PostMapping("/accounts-part")
    public String getAccountsViaAJAX(Model model, @PageableDefault(size=5) Pageable pageable )  {
        AccountData.AccountsResult accounts = accountTopLevelService.getAccounts(pageable);
        model.addAttribute("result", accounts);
        return "launchpad/account/accounts :: table";
    }

    @GetMapping(value = "/account-add")
    public String add(@ModelAttribute("account") Account account) {
        return "launchpad/account/account-add";
    }

    @PostMapping("/account-add-commit")
    public String addFormCommit(Model model, Account account, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = accountTopLevelService.addAccount(account);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.errorMessages);
            return "launchpad/account/account-add";
        }
        return "redirect:/launchpad/account/accounts";
    }

    @GetMapping(value = "/account-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes){
        AccountData.AccountResult accountResult = accountTopLevelService.getAccount(id);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/account/accounts";
        }
        accountResult.account.setPassword(null);
        accountResult.account.setPassword2(null);
        model.addAttribute("account", accountResult.account);
        return "launchpad/account/account-edit";
    }

    @PostMapping("/account-edit-commit")
    public String editFormCommit(Long id, String publicName, boolean enabled, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = accountTopLevelService.editFormCommit(id, publicName, enabled);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/account/accounts";
    }

    @GetMapping(value = "/account-password-edit/{id}")
    public String passwordEdit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes){
        AccountData.AccountResult accountResult = accountTopLevelService.getAccount(id);
        if (accountResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", accountResult.errorMessages);
            return "redirect:/launchpad/account/accounts";
        }
        accountResult.account.setPassword(null);
        accountResult.account.setPassword2(null);
        model.addAttribute("account", accountResult.account);
        return "launchpad/account/account-password-edit";
    }

    @PostMapping("/account-password-edit-commit")
    public String passwordEditFormCommit(Long id, String password, String password2, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = accountTopLevelService.passwordEditFormCommit(id, password, password2);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/account/accounts";
    }
}