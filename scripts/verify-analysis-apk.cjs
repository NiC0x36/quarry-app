// Verify the distributed APK, not just the source manifest.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const apk = process.argv[2];
if (!apk) throw new Error('Usage: node scripts/verify-analysis-apk.cjs path/to/app.apk [report.json]');
const inspected = JSON.parse(cp.execFileSync(process.execPath, [path.join(__dirname, 'inspect-apk.cjs'), apk], { encoding: 'utf8', maxBuffer: 4000000 }));
const manifest = inspected.manifest;
const application = manifest.children.find(e => e.tag === 'application');
assert.ok(['io.github.nic0x36.quarry.local', 'io.github.nic0x36.quarry.local.debug'].includes(manifest.attributes.package), 'APK must use the independent fork identity');
const permissions = manifest.children.filter(e => e.tag.startsWith('uses-permission')).map(e => e.attributes.name);
const allowedPermissions = new Set([
  'MANAGE_EXTERNAL_STORAGE', 'VIBRATE', 'POST_NOTIFICATIONS', 'FOREGROUND_SERVICE',
  'FOREGROUND_SERVICE_DATA_SYNC', 'USE_BIOMETRIC', 'USE_FINGERPRINT', 'WAKE_LOCK',
  'ACCESS_NETWORK_STATE', 'RECEIVE_BOOT_COMPLETED'
].map(e => 'android.permission.' + e));
allowedPermissions.add(manifest.attributes.package + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION');
for (const permission of permissions) assert.ok(allowedPermissions.has(permission), 'Unreviewed permission: ' + permission);
for (const permission of ['INTERNET', 'REQUEST_INSTALL_PACKAGES', 'PACKAGE_USAGE_STATS', 'QUERY_ALL_PACKAGES', 'WRITE_EXTERNAL_STORAGE']) {
  assert.ok(!permissions.includes('android.permission.' + permission), 'Unexpected permission: ' + permission);
}
assert.equal(application.attributes.allowBackup, false, 'Backup must be disabled in the merged manifest');
assert.ok(application.attributes.fullBackupContent?.type === 1, 'Legacy backup rules must be referenced');
assert.ok(application.attributes.dataExtractionRules?.type === 1, 'Android 12+ extraction rules must be referenced');
assert.ok(!application.children.some(e => e.tag === 'provider' && /FileProvider/.test(e.attributes.name)), 'File-sharing provider must not be present');
const domains = ['root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref'];
function allExcluded(node, label) {
  assert.ok(node, label + ' missing from APK');
  assert.ok(!node.children.some(e => e.tag === 'include'), label + ' must not include any files');
  for (const domain of domains) assert.ok(node.children.some(e => e.tag === 'exclude' && e.attributes.domain === domain && e.attributes.path === '.'), label + ': missing exclusion for ' + domain);
}
const legacyVariants = inspected.backupBindings?.fullBackupContent?.variants;
const extractionVariants = inspected.backupBindings?.dataExtractionRules?.variants;
assert.ok(legacyVariants?.length > 0, 'Manifest legacy-backup reference must resolve');
assert.ok(extractionVariants?.length > 0, 'Manifest extraction reference must resolve');
for (const variant of legacyVariants) {
  assert.equal(variant.xml?.tag, 'full-backup-content', 'Wrong legacy-backup resource');
  allExcluded(variant.xml, 'Legacy backup: ' + variant.path);
}
for (const variant of extractionVariants) {
  assert.equal(variant.xml?.tag, 'data-extraction-rules', 'Wrong extraction resource');
  allExcluded(variant.xml.children.find(e => e.tag === 'cloud-backup'), 'Cloud backup: ' + variant.path);
  allExcluded(variant.xml.children.find(e => e.tag === 'device-transfer'), 'Device transfer: ' + variant.path);
}
assert.ok(inspected.signing.v2.length > 0, 'A verified APK v2 signature is required');
for (const signer of inspected.signing.v2) {
  assert.equal(signer.certificateMatchesPublicKey, true);
  assert.ok(signer.checked.some(e => e.signatureValid && e.apkContentDigestValid), 'APK signature/content check failed');
}
for (const dex of inspected.dex) assert.equal(dex.trackerAndNetworkLibraryMarkers.length, 0, 'Unexpected tracker/network SDK marker in DEX');
const report = {
  package: manifest.attributes.package,
  version: manifest.attributes.versionName,
  sha256: inspected.sha256,
  size: inspected.bytes,
  privacyChecksPassed: true,
  signatureAndContentValid: true,
  permissions,
  certificateSha256: inspected.signing.v2.map(e => e.certificateSha256),
  limitations: 'Static APK check. No physical-device or backup-transport runtime test.'
};
if (process.argv[3]) fs.writeFileSync(process.argv[3], JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
