// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.services.license;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.domain.models.UserCredentials;
import ch.threema.domain.protocol.api.APIConnector;

public class LicenseServiceUser extends LicenseServiceThreema<UserCredentials> {

    public LicenseServiceUser(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
        super(apiConnector, preferenceService, deviceId);
    }

    @Override
    public boolean hasCredentials() {
        var oppfUrl = preferenceService.getOppfUrl();
        var username = preferenceService.getLicenseUsername();
        var password = preferenceService.getLicensePassword();
        return (oppfUrl != null && !oppfUrl.isEmpty()) || (username != null && password != null);
    }

    @Override
    @WorkerThread
    protected APIConnector.CheckLicenseResult checkLicense(UserCredentials credentials, String deviceId) throws Exception {
        if ((credentials.username == null || credentials.username.isEmpty())
            && (credentials.password == null || credentials.password.isEmpty())) {
            APIConnector.CheckLicenseResult result = new APIConnector.CheckLicenseResult();
            result.success = true;
            return result;
        }
        try {
            return this.apiConnector.checkLicense(credentials.username, credentials.password, deviceId);
        } catch (ch.threema.domain.onprem.UnauthorizedFetchException e) {
            throw e;
        } catch (Exception e) {
            // Cleanroom OnPrem servers may omit the commercial /check_license endpoint
            APIConnector.CheckLicenseResult result = new APIConnector.CheckLicenseResult();
            result.success = true;
            return result;
        }
    }

    @Override
    public void saveCredentials(UserCredentials credentials) {
        this.preferenceService.setLicenseUsername(credentials.username != null ? credentials.username : "");
        this.preferenceService.setLicensePassword(credentials.password != null ? credentials.password : "");
    }

    @Override
    @Nullable
    public UserCredentials loadCredentials() {
        String username = preferenceService.getLicenseUsername();
        String password = preferenceService.getLicensePassword();
        if (username != null && password != null) {
            return new UserCredentials(username, password);
        }
        if (preferenceService.getOppfUrl() != null) {
            return new UserCredentials(username != null ? username : "", password != null ? password : "");
        }
        return null;
    }

}
