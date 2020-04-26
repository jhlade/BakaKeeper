package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

/**
 * Callback pro neinteraktivní předání přihlašovacích údajů pro Kerberos V.
 *
 * @author Jan Hladěna
 */
public class BakaKerberosCallback implements CallbackHandler {

    public void handle(Callback[] callbacks) {

        for (int i = 0; i < callbacks.length; i++) {

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Kerberos: callback [" + i + "].");
            }

            if (callbacks[i] instanceof NameCallback) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Kerberos: probíhá ověření uživatelského jména.");
                }

                NameCallback usernameCallback = (NameCallback) callbacks[i];
                usernameCallback.setName(Settings.getInstance().getKrb_user());
            } else if (callbacks[i] instanceof PasswordCallback) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Kerberos: probíhá ověření hesla.");
                }

                PasswordCallback passwordCallback = (PasswordCallback) callbacks[i];
                passwordCallback.setPassword(Settings.getInstance().getPass().toCharArray());
            }
        }

    }
}