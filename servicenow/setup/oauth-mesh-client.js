/**
 * ITSM AI agent mesh - OAuth client setup (one per ServiceNow instance)
 *
 * Creates or updates, idempotently:
 *   1. the mesh's signing certificate            sys_certificate (Trust Store Cert, PEM)
 *   2. the OAuth JWT API endpoint for the mesh   oauth_jwt (JWT bearer grant, "Integration as a User",
 *                                                 securely scoped, JTI replay protection)
 *   3. the JWT verifier map (kid -> certificate) jwt_verifier_map
 *   4. issuer claim validation (iss = client_id) oauth_jwt_claims
 *   5. the restriction to the app's auth scope   oauth_entity_auth_scope_mapping -> x_1320561_itsm_ai.mesh_api
 * and reports which of the app's REST APIs are bound to that auth scope (bind them in the UI, see the runbook).
 *
 * Run as an admin in System Definition > Scripts - Background, with the application scope set to "global",
 * AFTER the x_1320561_itsm_ai app is installed. Nothing secret is stored in this file: paste the PUBLIC
 * certificate only. The client secret is generated here and printed once (on creation or rotation) - put it
 * straight into your secret store and clear the script output.
 *
 * Key rotation: generate a new key pair, set CONFIG.keyId to a new value and CONFIG.certificatePem to the new
 * certificate, and run again. The old verifier map entry stays until you remove it, so both keys work during
 * the switch-over (see docs/servicenow-oauth.md).
 */
(function setupMeshOAuthClient() {
    var CONFIG = {
        registryName: 'ITSM AI Agent Mesh',
        keyId: 'mesh-2026-10',                       // must equal the mesh's SN_OAUTH_KEY_ID
        certificatePem: [                            // PUBLIC certificate only; one quoted line per PEM line
            '-----BEGIN CERTIFICATE-----',
            'MIIENTCCAp2gAwIBAgIUYXkYmRdrDdk9RG7RE0IC1v7guqcwDQYJKoZIhvcNAQEL',
            'BQAwKjEoMCYGA1UEAwwfSVRTTSBBSSBBZ2VudCBNZXNoIG1lc2gtMjAyNi0xMDAe',
            'Fw0yNjEwMDYyMTUwNTdaFw0yNzEwMDYyMTUwNTdaMCoxKDAmBgNVBAMMH0lUU00g',
            'QUkgQWdlbnQgTWVzaCBtZXNoLTIwMjYtMTAwggGiMA0GCSqGSIb3DQEBAQUAA4IB',
            'jwAwggGKAoIBgQCT+cKip70DJOssVnph8hLNKXnJstFbQGq4sYq6FJoUaYchuDF3',
            'Kvry3RXggWJca3/dJ78u9EQjlAe4fXua/HFeYxHkGShpQMvQxzh9FyyfSgrptXFY',
            'u2H9ZB+54ATeMcLR5IcRVjoIsaJxHmZsR1eFs1lv5vRNo/5PpeO9Xyc0zc5IC2Mr',
            'OWL3xIXWWa+XbAo3MRhsZ+NpWNG7TQSnEdREQRXBD8Yeny9QLFXtYp4i5QIOL7Gm',
            'QXhFTx+AG2w/79IPjdwMC5g4pCTXTT58kiugKTa9Z02taV8LLLLOpYnE2hIdJxMy',
            'IYrtU4LbwNmFXWadv1FFGF787xwp6C/HZZVu1DKOs3yfGtHjfm6Q/qOEKIfo4TgP',
            '2yTG3wFUwvL6nlNlUgH/inHqHV/eGlMr7JI2Nis34dnbEak4fSjlm0nXtDoZvJlK',
            'euu6ftGk/VVFPwW0xPXDZ62z6bBZeldyEev35kM4NaZrJ5mmkLU6UAoppbqufGcD',
            '7BpqOEkMP5l7KpUCAwEAAaNTMFEwHQYDVR0OBBYEFBJHhTZtHu3wx9D56kslCTf+',
            'w73/MB8GA1UdIwQYMBaAFBJHhTZtHu3wx9D56kslCTf+w73/MA8GA1UdEwEB/wQF',
            'MAMBAf8wDQYJKoZIhvcNAQELBQADggGBACpYYf1up0faAsH3Mj3Tz9tGAIQAW1HU',
            'hPDkM/vyyyQiAbRYEhnpZJ06I5+UfvHaKRNRwCndeWLDUW48APs+jws+dEX9sPuw',
            '055lqPvtcAPQ47oR5bH19L5G5J7fRPd5ufd+JEaHTtgiQFzy2DXePbxHQ4xVuTNu',
            '/E6rui4QMKxFgiER8rlmqIA3J/AsmorgTUz4of7gV5QaF+SGcJ2Xbd7MLsd7c/Gh',
            'XNhLVVacEeecNTIMbFRCaOBhbY1AIvVc9M0pzD3dc9I5N0kZ3mJeBPDCUiVLDIWM',
            '7vqle6DnNm4N4vUXOjbN2OGV8LGg96FdDcHyXdMOWdgJ2yOTa3cShgf1MtjFccwy',
            'QynN/dnY3B5lp879WggwxlRwybxJY5y7L6N9Nf06UN/I4EYZ1iX3q6VkzEdtoi3S',
            'tiDmjAwlDkpAFdOFkWWbjEaS1JJ88cQqAU/XUCwShIa1ZX2QldwKhzoXWMpHSWUt',
            'hRP15XVOoCiVUIYmgYZhqj6/rPMCzFQTLA==',
            '-----END CERTIFICATE-----'
        ].join('\n'),
        userField: 'user_name',                      // sys_user field equal to the value the mesh sends as "sub"
        accessTokenLifespan: 1800,                   // seconds; the mesh refreshes 5 minutes before expiry
        clockSkew: 60,                               // seconds of allowed clock drift for exp/nbf
        authScope: 'x_1320561_itsm_ai.mesh_api',     // installed by the scoped app
        apiNamespace: 'x_1320561_itsm_ai',
        apis: ['incident', 'problem', 'change', 'catalog', 'cmdb', 'kb', 'access'],
        rotateSecret: false                          // true issues a new client secret (the old one stops working)
    };

    var out = [];
    function say(msg) { out.push(msg); }
    function fail(msg) { gs.print('ERROR: ' + msg); throw new Error(msg); }
    function findOne(table, query) {
        var gr = new GlideRecord(table);
        gr.addEncodedQuery(query);
        gr.setLimit(1);
        gr.query();
        return gr.next() ? gr : null;
    }
    function newSecret() {
        try {
            return GlideSecureRandomUtil.getSecureRandomString(48);
        } catch (e) {
            return (gs.generateGUID() + gs.generateGUID() + gs.generateGUID()).substring(0, 64);
        }
    }

    // ---------------------------------------------------------------------------------------- checks
    if (gs.getCurrentScopeName() !== 'rhino.global' && gs.getCurrentScopeName() !== 'global') {
        fail('Run this script in the global scope.');
    }
    var pem = String(CONFIG.certificatePem).trim();
    if (pem.indexOf('-----BEGIN CERTIFICATE-----') !== 0 || pem.indexOf('PRIVATE KEY') >= 0) {
        fail('CONFIG.certificatePem must be the PUBLIC X.509 certificate in PEM format (never the private key).');
    }
    var userFieldOk = new GlideRecord('sys_user').isValidField(CONFIG.userField);
    if (!userFieldOk) fail('sys_user has no field "' + CONFIG.userField + '".');
    var scope = findOne('sys_auth_scope', 'name=' + CONFIG.authScope);
    if (!scope) fail('Auth scope ' + CONFIG.authScope + ' not found. Install or upgrade the x_1320561_itsm_ai app first.');

    // ---------------------------------------------------------------------------------------- 1. certificate
    var certName = CONFIG.registryName + ' signing certificate (' + CONFIG.keyId + ')';
    var cert = findOne('sys_certificate', 'name=' + certName);
    if (!cert) {
        cert = new GlideRecord('sys_certificate');
        cert.initialize();
        cert.setValue('name', certName);
        say('Certificate: created ' + certName);
    } else {
        say('Certificate: updated ' + certName);
    }
    cert.setValue('type', 'trust_store');
    cert.setValue('format', 'pem');
    cert.setValue('pem_certificate', pem);
    cert.setValue('active', true);
    cert.setValue('frequency', 'one-time');
    cert.setValue('expiration_notification', true);
    cert.setValue('warn_in_days_to_expire', 30);
    cert.setValue('short_description', 'Public key that verifies the ITSM AI agent mesh JWT assertions (kid ' + CONFIG.keyId + ').');
    var certId = cert.isNewRecord() ? cert.insert() : (cert.update() && cert.getUniqueValue());
    if (!certId) fail('Could not save the certificate.');

    // ---------------------------------------------------------------------------------------- 2. registry
    var reg = findOne('oauth_jwt', 'name=' + CONFIG.registryName);
    var secret = null;
    if (!reg) {
        reg = new GlideRecord('oauth_jwt');
        reg.initialize();
        reg.setValue('name', CONFIG.registryName);
        secret = newSecret();
        say('OAuth registry: created ' + CONFIG.registryName);
    } else {
        say('OAuth registry: updated ' + CONFIG.registryName);
        if (CONFIG.rotateSecret) secret = newSecret();
    }
    if (secret) reg.setValue('client_secret', secret);
    reg.setValue('active', true);
    reg.setValue('inbound_grant_type', 'jwt');
    reg.setValue('client_type', 'integration_as_a_user');
    reg.setValue('user_field', CONFIG.userField);
    reg.setValue('enable_jti_verification', true);
    reg.setValue('jti_claim', 'jti');
    reg.setValue('clock_skew', CONFIG.clockSkew);
    reg.setValue('access_token_lifespan', CONFIG.accessTokenLifespan);
    reg.setValue('refresh_token_lifespan', CONFIG.accessTokenLifespan);
    reg.setValue('scope_restriction_status', 'restricted');
    reg.setValue('token_format', 'opaque');
    reg.setValue('comments', 'Confidential client of the ITSM AI agent mesh. Obtains short-lived tokens on behalf of '
        + 'signed-in users (JWT bearer). Restricted to auth scope ' + CONFIG.authScope + '.');
    var regId = reg.isNewRecord() ? reg.insert() : (reg.update() && reg.getUniqueValue());
    if (!regId) fail('Could not save the OAuth registry.');
    reg = findOne('oauth_jwt', 'sys_id=' + regId);
    var clientId = String(reg.getValue('client_id'));

    // ---------------------------------------------------------------------------------------- 3. verifier map
    var map = findOne('jwt_verifier_map', 'oauth_jwt=' + regId + '^kid=' + CONFIG.keyId);
    if (!map) {
        map = new GlideRecord('jwt_verifier_map');
        map.initialize();
        map.setValue('oauth_jwt', regId);
        map.setValue('kid', CONFIG.keyId);
        say('Verifier map: added kid ' + CONFIG.keyId);
    } else {
        say('Verifier map: kid ' + CONFIG.keyId + ' already present (certificate refreshed)');
    }
    map.setValue('name', CONFIG.registryName + ' ' + CONFIG.keyId);
    map.setValue('sys_certificate', certId);
    if (!(map.isNewRecord() ? map.insert() : map.update())) fail('Could not save the JWT verifier map.');
    var others = new GlideRecord('jwt_verifier_map');
    others.addQuery('oauth_jwt', regId);
    others.addQuery('kid', '!=', CONFIG.keyId);
    others.query();
    while (others.next()) say('  note: older key still accepted: kid ' + others.getValue('kid') + ' (remove it once the mesh uses ' + CONFIG.keyId + ')');

    // ---------------------------------------------------------------------------------------- 4. claim validation
    if (!findOne('oauth_jwt_claims', 'oauth_jwt=' + regId + '^name=iss')) {
        var claim = new GlideRecord('oauth_jwt_claims');
        claim.initialize();
        claim.setValue('oauth_jwt', regId);
        claim.setValue('name', 'iss');
        claim.setValue('claim_value', clientId);
        claim.setValue('claim_value_type', 'string');
        if (!claim.insert()) fail('Could not save the issuer claim validation.');
        say('Claim validation: iss must equal the client id');
    }

    // ---------------------------------------------------------------------------------------- 5. auth scope
    if (!findOne('oauth_entity_auth_scope_mapping', 'oauth_entity=' + regId + '^auth_scope=' + scope.getUniqueValue())) {
        var m = new GlideRecord('oauth_entity_auth_scope_mapping');
        m.initialize();
        m.setValue('oauth_entity', regId);
        m.setValue('auth_scope', scope.getUniqueValue());
        if (!m.insert()) fail('Could not map the auth scope.');
        say('Auth scope: tokens restricted to ' + CONFIG.authScope);
    }

    // ---------------------------------------------------------------------------------------- report bindings
    var missing = [];
    for (var i = 0; i < CONFIG.apis.length; i++) {
        var bound = new GlideRecord('sys_api_access_scope');
        bound.addQuery('auth_scope', scope.getUniqueValue());
        bound.addQuery('active', true);
        bound.addQuery('api_path', 'CONTAINS', '/' + CONFIG.apiNamespace + '/' + CONFIG.apis[i]);
        bound.setLimit(1);
        bound.query();
        if (!bound.hasNext()) missing.push(CONFIG.apis[i]);
    }

    gs.print('\n===== ITSM AI agent mesh OAuth client =====\n' + out.join('\n'));
    gs.print('\nToken endpoint : ' + gs.getProperty('glide.servlet.uri') + 'oauth_token.do');
    gs.print('Client id      : ' + clientId + '      -> SN_OAUTH_CLIENT_ID');
    gs.print('Key id (kid)   : ' + CONFIG.keyId + '      -> SN_OAUTH_KEY_ID');
    gs.print('User field     : sys_user.' + CONFIG.userField + ' (the mesh sends this value as "sub")');
    if (secret) {
        gs.print('Client secret  : ' + secret + '      -> secret store, mounted as SN_OAUTH_CLIENT_SECRET_FILE');
        gs.print('                 Shown once. Store it now and clear this output.');
    } else {
        gs.print('Client secret  : unchanged (set rotateSecret: true to issue a new one)');
    }
    gs.print(missing.length
        ? '\nACTION NEEDED: bind these REST APIs to auth scope ' + CONFIG.authScope + ' (REST API Auth Scopes, see runbook): ' + missing.join(', ')
        : '\nAll ' + CONFIG.apis.length + ' REST APIs are bound to ' + CONFIG.authScope + '.');
})();
