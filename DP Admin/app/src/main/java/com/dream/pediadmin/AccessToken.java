package com.dream.pediadmin;

import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AccessToken {


    private static final String firebaseMessagingScope = "https://www.googleapis.com/auth/firebase.messaging";


    public String getAccessToken() {

        try {
            String jsonString = "{\n" +
                    "  \"type\": \"service_account\",\n" +
                    "  \"project_id\": \"dreampedi\",\n" +
                    "  \"private_key_id\": \"9874a03c9627e5403b79758927dbb9b249b9ea3d\",\n" +
                    "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC/zgEpDpIf/J52\\nAKIDRsLpcRc2a19eS0oYU9Omt/qaT9Nwt6GIF3bQkS53MyoxgOhA13ChszUuHaz/\\nfIxfpaGVw7LWQ4rOA92AgQHxn52PZ90N/F+8dbK3sm8bZyfS3vvzcK+vrq8OqyjB\\nooGgeICjhd0arlB3JtP0Bnld+6pI5qh818dLnFtLJFw8ERaguH3PCt8uUyr967Ci\\n0XGp8pLu2eFhSVXDpsUQWOsFHFqz2HQjZ7fkwNkbxNnAD8ctzHZ1pidgfXVp58W0\\njG8jncyMUxPSBIHpYdScaFtSqFZ6AfKSk/zrThL3Ww+GkkrobbHi0dtos4gcFFx3\\nvFm/eVkxAgMBAAECggEAKKp9tn2/X4bI9g3mS7mKA8KVgFU9kBl4aTYErtldCbCZ\\nDNHyLo+DAm91oZI4hOxm0bCb0bzULqXeEwEOJg1Q+BbY6PLDEDDjmqY/ikIkPs5v\\noNJ8XdG6hZYQVogFeoEKfC/NH/tyZDLH3l4dC6/g3kq4eTqyFsEluRxSSSndjbzz\\nzHTJCuQqRQZyRxryGJghlGAq9RzDt3A6E5cqGWt5UXIQAuvntNmm0HvDqCU1hSYM\\n/XrvnNYjP2ETdvmMJE5ZAx1EHfd6zduJuns+yC+RSuUllbpIN9mWKm9N2kgCDbmk\\nSc3RSkqloOmMsfRE7DLJAM9+RIZ/oLlD14jsUlL+rQKBgQDhJca/SnoOF0vERH0H\\nYnWxlA2h2uWIUZ99R7hUqL+xlaRd9uAkU9UyLrPo6iM265PKnT0Bm0wX0abKjXoQ\\nlfVym/a2heiV0e9X7H4v0p5/f61AXEDjsPFPlCWOwT1JswA2U3yD47gem21L6YFH\\nc6C/v+O+XBYQsZubop3PrFgfywKBgQDaFo3AeGzEmrGHhdfyqGWUMavfnecbeYp2\\nwPpQc6ycnEPWtdu5D0Ce6OSWJJyCtsq2PsaO3S72uktnSAL2/MHI7BXtvsAeVtXt\\nsr1ckMrzK0wJnDLmPK49SgtDoYE54kxVW/XBXB2hKjfC/wueGQlapOuDps8Y/UZ2\\nymHrDbUTcwKBgQCwQ87xe/FKjtp2cdggq+jQecSibqk8rApdHoUlYmeRNEJWfizE\\nxA0pGH8pXgTrvksaWTelxlswWqZl9ZUW3xAmBgyEoRmSEaV8BF5WOmJ0FxUgShlT\\nKPgDAUzWEltVE5qa7YmSB/nhnHVY9RqNQe0bsbMvGRD9SM82bX13VMo5OQKBgQCA\\nablLtFzvItsXnmK6Ohedu+WlBPf4wIccR6LcTlA74vM68NgQzn9RUnaORb52ZSVV\\nS/9kTa6Cjs0pYkiEn+2G3iRHWgMLmjva+1zurT8jEcJzAisiUGaUJg4d43IQ4EPs\\nGvrIwPGdXRt6UOzJSZqtaeBXI5hb8X5VIfOoEOknKwKBgQC/e5nsLWej/4vB95i2\\n/GSPTuBOu4sYrzaX8Lz1egQTW+DYM8Rs/lhkNm9pPLdCiA5r68TCEoQ3Wo3MbK1g\\nY95YW088vPJkkgiQWbYQw1UG0srgRiuAqGg6EeJ1utu1qAN2w4mGAyEmqqs3BR0B\\nrV1Z9crYfNvg7scsSng7DACVxw==\\n-----END PRIVATE KEY-----\\n\",\n" +
                    "  \"client_email\": \"firebase-adminsdk-fbsvc@dreampedi.iam.gserviceaccount.com\",\n" +
                    "  \"client_id\": \"104253870767231353015\",\n" +
                    "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                    "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                    "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                    "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40dreampedi.iam.gserviceaccount.com\",\n" +
                    "  \"universe_domain\": \"googleapis.com\"\n" +
                    "}\n";

            InputStream stream =new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(stream).createScoped(firebaseMessagingScope);
            googleCredentials.refresh();
            return googleCredentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            Log.e("error", "" + e.getMessage());
            return null;
        }
    }
}
