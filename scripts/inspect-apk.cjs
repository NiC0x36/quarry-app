// Read-only APK inspection: ZIP, binary XML, DEX strings and v2 signature.
const fs = require('node:fs');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const path = require('node:path');
const filename = process.argv[2];
const apk = fs.readFileSync(filename);
let eocd = -1;
for (let p = apk.length - 22; p >= Math.max(0, apk.length - 65557); p--) {
  if (apk.readUInt32LE(p) === 0x06054b50 && p + 22 + apk.readUInt16LE(p + 20) === apk.length) { eocd = p; break; }
}
if (eocd < 0) throw new Error('ZIP EOCD missing');
const cd = apk.readUInt32LE(eocd + 16);
const entries = [];
for (let p = cd; p < eocd && apk.readUInt32LE(p) === 0x02014b50;) {
  const nl = apk.readUInt16LE(p + 28), el = apk.readUInt16LE(p + 30), cl = apk.readUInt16LE(p + 32);
  entries.push({ name: apk.toString('utf8', p + 46, p + 46 + nl), method: apk.readUInt16LE(p + 10), size: apk.readUInt32LE(p + 24), compressedSize: apk.readUInt32LE(p + 20), offset: apk.readUInt32LE(p + 42) });
  p += 46 + nl + el + cl;
}
function extract(entry) {
  const p = entry.offset;
  if (apk.readUInt32LE(p) !== 0x04034b50) throw new Error('ZIP local header');
  const start = p + 30 + apk.readUInt16LE(p + 26) + apk.readUInt16LE(p + 28);
  const bytes = apk.subarray(start, start + entry.compressedSize);
  const result = entry.method === 8 ? zlib.inflateRawSync(bytes) : entry.method === 0 ? bytes : null;
  if (!result || result.length !== entry.size) throw new Error('ZIP extraction mismatch');
  return result;
}
function parseXml(b) {
  let strings = [], root = null, stack = [];
  function len8(p) { const a = b[p]; return a & 128 ? [((a & 127) << 8) | b[p + 1], 2] : [a, 1]; }
  function len16(p) { const a = b.readUInt16LE(p); return a & 32768 ? [((a & 32767) << 16) | b.readUInt16LE(p + 2), 4] : [a, 2]; }
  for (let p = b.readUInt16LE(2); p < b.length;) {
    const type = b.readUInt16LE(p), header = b.readUInt16LE(p + 2), size = b.readUInt32LE(p + 4);
    if (size < header || !size || p + size > b.length) throw new Error('Bad XML chunk');
    if (type === 1) {
      const count = b.readUInt32LE(p + 8), utf8 = !!(b.readUInt32LE(p + 16) & 256), data = p + b.readUInt32LE(p + 20);
      strings = Array.from({ length: count }, (_, i) => {
        let q = data + b.readUInt32LE(p + header + i * 4);
        if (utf8) { let n = len8(q); q += n[1]; n = len8(q); q += n[1]; return b.toString('utf8', q, q + n[0]); }
        const n = len16(q); q += n[1]; return b.toString('utf16le', q, q + n[0] * 2);
      });
    } else if (type === 0x0102) {
      const node = { tag: strings[b.readUInt32LE(p + 20)], attributes: {}, children: [] };
      const count = b.readUInt16LE(p + 28), stride = b.readUInt16LE(p + 26);
      for (let i = 0, a = p + 16 + b.readUInt16LE(p + 24); i < count; i++, a += stride) {
        const name = strings[b.readUInt32LE(a + 4)], raw = b.readUInt32LE(a + 8), dt = b[a + 15], value = b.readUInt32LE(a + 16);
        node.attributes[name] = raw !== 0xffffffff ? strings[raw] : dt === 3 ? strings[value] : dt === 18 ? !!value : dt === 16 || dt === 17 ? value : { type: dt, value };
      }
      if (stack.length) stack.at(-1).children.push(node); else root = node;
      stack.push(node);
    } else if (type === 0x0103) stack.pop();
    p += size;
  }
  return root;
}
// Resource-table structures follow AOSP androidfw/ResourceTypes.h. Resolve the
// manifest's actual XML resource IDs, including aliases and configuration variants.
function resourceChunk(b, p, limit = b.length) {
  if (p < 0 || p + 8 > limit) throw new Error('Truncated resource chunk');
  const type = b.readUInt16LE(p), header = b.readUInt16LE(p + 2), size = b.readUInt32LE(p + 4);
  if (header < 8 || size < header || p + size > limit) throw new Error('Bad resource chunk');
  return { type, header, size, end: p + size };
}
function resourceStrings(b, p, limit) {
  const c = resourceChunk(b, p, limit);
  if (c.type !== 1 || c.header < 28) throw new Error('Resource string pool missing');
  const count = b.readUInt32LE(p + 8), utf8 = !!(b.readUInt32LE(p + 16) & 256);
  const data = p + b.readUInt32LE(p + 20);
  if (p + c.header + count * 4 > c.end || data > c.end) throw new Error('Bad resource string offsets');
  const len8 = q => {
    if (q >= c.end) throw new Error('Truncated UTF8 resource length');
    const a = b[q];
    if (a & 128) { if (q + 2 > c.end) throw new Error('Truncated UTF8 resource length'); return [((a & 127) << 8) | b[q + 1], 2]; }
    return [a, 1];
  };
  const len16 = q => {
    if (q + 2 > c.end) throw new Error('Truncated UTF16 resource length');
    const a = b.readUInt16LE(q);
    if (a & 32768) { if (q + 4 > c.end) throw new Error('Truncated UTF16 resource length'); return [(a & 32767) * 65536 + b.readUInt16LE(q + 2), 4]; }
    return [a, 2];
  };
  return Array.from({ length: count }, (_, i) => {
    let q = data + b.readUInt32LE(p + c.header + i * 4);
    let n;
    if (utf8) { n = len8(q); q += n[1]; n = len8(q); q += n[1]; }
    else { n = len16(q); q += n[1]; n[0] *= 2; }
    if (q + n[0] > c.end) throw new Error('Truncated resource string');
    return b.toString(utf8 ? 'utf8' : 'utf16le', q, q + n[0]);
  });
}
function xmlResourceEntries(b) {
  const table = resourceChunk(b, 0);
  if (table.type !== 2 || table.header < 12) throw new Error('Resource table missing');
  let values = null;
  const records = new Map();
  for (let p = table.header; p < table.end;) {
    const c = resourceChunk(b, p, table.end);
    if (c.type === 1) values = resourceStrings(b, p, c.end);
    else if (c.type === 0x0200) {
      if (c.header < 284 || !values) throw new Error('Unsupported resource package');
      const packageId = b.readUInt32LE(p + 8), typeOffset = c.header >= 288 ? b.readUInt32LE(p + 284) : 0;
      const types = resourceStrings(b, p + b.readUInt32LE(p + 268), c.end);
      const keys = resourceStrings(b, p + b.readUInt32LE(p + 276), c.end);
      for (let q = p + c.header; q < c.end;) {
        const t = resourceChunk(b, q, c.end);
        if (t.type === 0x0201 && types[b[q + 8] - 1] === 'xml') {
          if (t.header < 24) throw new Error('Truncated XML resource type');
          const typeId = b[q + 8] + typeOffset, flags = b[q + 9], count = b.readUInt32LE(q + 12);
          const entriesStart = q + b.readUInt32LE(q + 16), configSize = b.readUInt32LE(q + 20);
          const stride = flags & 2 ? 2 : 4;
          if (flags & ~3 || (flags & 3) === 3 || configSize < 4 || 20 + configSize > t.header ||
              q + t.header + count * stride > entriesStart || entriesStart > t.end || packageId > 255 || typeId > 255) {
            throw new Error('Unsupported XML resource layout');
          }
          const configuration = b.subarray(q + 24, q + 20 + configSize).toString('hex');
          for (let i = 0; i < count; i++) {
            const indexOffset = q + t.header + i * stride;
            const index = flags & 1 ? b.readUInt16LE(indexOffset) : i;
            const rawOffset = flags & 1 ? b.readUInt16LE(indexOffset + 2) : flags & 2 ? b.readUInt16LE(indexOffset) : b.readUInt32LE(indexOffset);
            if (rawOffset === (flags & 2 ? 0xffff : 0xffffffff)) continue;
            const e = entriesStart + (flags & 3 ? rawOffset * 4 : rawOffset);
            if (e + 8 > t.end) throw new Error('Truncated XML resource entry');
            const entryFlags = b.readUInt16LE(e + 2), compact = !!(entryFlags & 8);
            if (entryFlags & 1) throw new Error('XML resource must have a simple value');
            const keyIndex = compact ? b.readUInt16LE(e) : b.readUInt32LE(e + 4);
            const v = e + b.readUInt16LE(e);
            if (!compact && (v < e + 8 || v + 8 > t.end)) throw new Error('Bad XML resource value');
            const dataType = compact ? entryFlags >>> 8 : b[v + 3];
            const data = b.readUInt32LE(compact ? e + 4 : v + 4);
            const id = ((packageId << 24) | (typeId << 16) | index) >>> 0;
            const record = { resourceName: 'xml/' + keys[keyIndex], configuration, dataType, data };
            if (!keys[keyIndex]) throw new Error('XML resource key missing');
            if (!records.has(id)) records.set(id, []);
            records.get(id).push(record);
          }
        }
        q = t.end;
      }
    }
    p = c.end;
  }
  return { records, values };
}
function resolveBackupBindings(manifest) {
  const entry = entries.find(e => e.name === 'resources.arsc');
  if (!entry) throw new Error('Resource table missing from APK');
  const { records, values } = xmlResourceEntries(extract(entry));
  const app = manifest.children.find(e => e.tag === 'application');
  const resolve = (id, visiting = new Set()) => {
    if (visiting.has(id)) throw new Error('Cyclic XML resource reference');
    const next = new Set(visiting).add(id);
    return (records.get(id) || []).flatMap(record => {
      if (record.dataType === 1) return resolve(record.data, next).map(target => ({
        ...target, aliases: [record.resourceName, ...(target.aliases || [])]
      }));
      if (record.dataType !== 3) throw new Error('Backup resource is not an XML file');
      const filename = values[record.data], file = entries.find(e => e.name === filename);
      if (!file || !/^res\/xml(?:-[^/]+)?\/.*\.xml$/.test(filename)) throw new Error('Referenced backup XML missing');
      return [{ resourceName: record.resourceName, configuration: record.configuration, path: filename, xml: parseXml(extract(file)) }];
    });
  };
  return Object.fromEntries(['fullBackupContent', 'dataExtractionRules'].map(name => {
    const reference = app?.attributes[name];
    const resourceId = reference?.type === 1 ? reference.value : null;
    return [name, { resourceId, variants: resourceId == null ? [] : resolve(resourceId) }];
  }));
}

function lp(b, state) { const n = b.readUInt32LE(state.p); state.p += 4; const out = b.subarray(state.p, state.p + n); state.p += n; if (out.length !== n) throw new Error('Length prefix'); return out; }
function seq(b) { const out = [], s = { p: 0 }; while (s.p < b.length) out.push(lp(b, s)); return out; }
const signing = { schemes: [], v2: [] };
if (apk.toString('ascii', cd - 16, cd) === 'APK Sig Block 42') {
  const size = Number(apk.readBigUInt64LE(cd - 24)), start = cd - size - 8;
  if (Number(apk.readBigUInt64LE(start)) !== size) throw new Error('Signing block size mismatch');
  for (let p = start + 8; p < cd - 24;) {
    const n = Number(apk.readBigUInt64LE(p)), id = apk.readUInt32LE(p + 8), value = apk.subarray(p + 12, p + 8 + n);
    signing.schemes.push('0x' + id.toString(16));
    if (id === 0x7109871a) {
      const signers = lp(value, { p: 0 });
      for (const signer of seq(signers)) {
        const s = { p: 0 }, signedData = lp(signer, s), signatures = lp(signer, s), publicKey = lp(signer, s);
        const d = { p: 0 }, digests = seq(lp(signedData, d)), certs = seq(lp(signedData, d));
        const cert = new crypto.X509Certificate(certs[0]);
        const checked = [];
        for (const signature of seq(signatures)) {
          const sigs = { p: 4 }, algo = signature.readUInt32LE(0), sig = lp(signature, sigs);
          const hash = [0x0102, 0x0104, 0x0202].includes(algo) ? 'sha512' : 'sha256';
          if (![0x0101, 0x0102, 0x0103, 0x0104, 0x0201, 0x0202, 0x0301].includes(algo)) { checked.push({ algo, unsupported: true }); continue; }
          const key = crypto.createPublicKey({ key: publicKey, format: 'der', type: 'spki' });
          const options = { key };
          if ([0x0101, 0x0102].includes(algo)) { options.padding = crypto.constants.RSA_PKCS1_PSS_PADDING; options.saltLength = hash === 'sha256' ? 32 : 64; }
          const signatureValid = crypto.verify(hash, signedData, options, sig);
          const end = Buffer.from(apk.subarray(eocd)); end.writeUInt32LE(start, 16);
          const pieces = [apk.subarray(0, start), apk.subarray(cd, eocd), end], hashes = [];
          for (const piece of pieces) for (let off = 0; off < piece.length; off += 1048576) {
            const chunk = piece.subarray(off, Math.min(off + 1048576, piece.length));
            const head = Buffer.alloc(5); head[0] = 0xa5; head.writeUInt32LE(chunk.length, 1);
            hashes.push(crypto.createHash(hash).update(head).update(chunk).digest());
          }
          const head = Buffer.alloc(5); head[0] = 0x5a; head.writeUInt32LE(hashes.length, 1);
          const calculated = crypto.createHash(hash).update(head).update(Buffer.concat(hashes)).digest();
          const record = digests.find(x => x.readUInt32LE(0) === algo), declared = record ? lp(record, { p: 4 }) : null;
          checked.push({ algorithm: '0x' + algo.toString(16), signatureValid, apkContentDigestValid: !!declared && calculated.equals(declared) });
        }
        signing.v2.push({ subject: cert.subject, issuer: cert.issuer, certificateSha256: cert.fingerprint256, validFrom: cert.validFrom, validTo: cert.validTo, certificateMatchesPublicKey: cert.publicKey.export({ type: 'spki', format: 'der' }).equals(publicKey), checked });
      }
    }
    p += 8 + n;
  }
}
const dexReports = [];
for (const entry of entries.filter(e => /^classes\d*\.dex$/.test(e.name))) {
  const b = extract(entry), count = b.readUInt32LE(56), offset = b.readUInt32LE(60), strings = [];
  for (let i = 0; i < count; i++) {
    let p = b.readUInt32LE(offset + i * 4); while (b[p++] & 128) {} const end = b.indexOf(0, p); strings.push(b.toString('utf8', p, end));
  }
  const markers = strings.filter(x => /firebase|crashlytics|admob|app-measurement|google-analytics|okhttp|retrofit|appsflyer|bugsnag|sentry\.io|mixpanel|facebook\/ads|unity3d\/ads/i.test(x));
  const urls = strings.filter(x => /^https?:\/\//.test(x));
  dexReports.push({ name: entry.name, stringCount: count, trackerAndNetworkLibraryMarkers: markers, urls });
}
const manifest = parseXml(extract(entries.find(e => e.name === 'AndroidManifest.xml')));
const report = {
  file: path.resolve(filename), bytes: apk.length, sha256: crypto.createHash('sha256').update(apk).digest('hex'),
  zipEntryCount: entries.length, manifest, signing,
  backupBindings: resolveBackupBindings(manifest),
  nativeLibraries: entries.filter(e => e.name.endsWith('.so')).map(e => e.name),
  backupResources: entries.filter(e => /^res\/xml\/.*\.xml$/.test(e.name)).map(e => ({ name: e.name, xml: parseXml(extract(e)) })).filter(e => ['full-backup-content', 'data-extraction-rules'].includes(e.xml?.tag)),
  executableArchives: entries.filter(e => /\.(jar|dex|apk)$/.test(e.name)).map(e => e.name), dex: dexReports
};
const output = process.argv[3]; if (output) fs.writeFileSync(output, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
