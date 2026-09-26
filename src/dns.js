const TYPE_A = 1;
const TYPE_AAAA = 28;
const TYPE_CNAME = 5;
const TYPE_SOA = 6;
const SOA_RDLENGTH = 24;

export const QUERY_TYPES = {
  1: "A",
  2: "NS",
  5: "CNAME",
  6: "SOA",
  12: "PTR",
  15: "MX",
  16: "TXT",
  28: "AAAA",
  33: "SRV",
  35: "NAPTR",
  43: "DS",
  48: "DNSKEY",
  64: "SVCB",
  65: "HTTPS",
  257: "CAA"
};

export function readQuestion(message) {
  if (message.length < 13) return null;
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  if (view.getUint16(4) !== 1) return null;
  const labels = [];
  let offset = 12;
  while (offset < message.length) {
    const length = message[offset];
    if (length === 0) {
      offset += 1;
      break;
    }
    if (length > 63 || offset + length + 1 > message.length) return null;
    labels.push(String.fromCharCode(...message.subarray(offset + 1, offset + 1 + length)));
    offset += length + 1;
  }
  if (labels.length === 0 || offset + 4 > message.length) return null;
  return {
    name: labels.join(".").toLowerCase(),
    type: view.getUint16(offset),
    class: view.getUint16(offset + 2),
    end: offset + 4
  };
}

const TYPE_OPT = 41;
const OPT_LENGTH = 11;
const MIN_PAYLOAD = 512;
const MAX_PAYLOAD = 4096;

export function ednsPayload(message, question) {
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  if (view.getUint16(10) === 0) return 0;
  const at = question.end;
  if (at + OPT_LENGTH > message.length || message[at] !== 0 || view.getUint16(at + 1) !== TYPE_OPT) return 0;
  return Math.min(MAX_PAYLOAD, Math.max(MIN_PAYLOAD, view.getUint16(at + 3)));
}

export function blockedResponse(message, question, ttl) {
  const questionBytes = message.subarray(12, question.end);
  const address = question.type === TYPE_A ? 4 : question.type === TYPE_AAAA ? 16 : 0;
  const rdlength = address > 0 ? address : SOA_RDLENGTH;
  const payload = ednsPayload(message, question);
  const response = new Uint8Array(12 + questionBytes.length + 12 + rdlength + (payload > 0 ? OPT_LENGTH : 0));
  const view = new DataView(response.buffer);

  response.set(message.subarray(0, 2), 0);
  response[2] = 0x80 | (message[2] & 0x01);
  response[3] = 0x80;
  view.setUint16(4, 1);
  view.setUint16(6, address > 0 ? 1 : 0);
  view.setUint16(8, address > 0 ? 0 : 1);
  view.setUint16(10, payload > 0 ? 1 : 0);
  response.set(questionBytes, 12);

  const offset = 12 + questionBytes.length;
  view.setUint16(offset, 0xc00c);
  view.setUint16(offset + 2, address > 0 ? question.type : TYPE_SOA);
  view.setUint16(offset + 4, question.class);
  view.setUint32(offset + 6, ttl);
  view.setUint16(offset + 10, rdlength);
  const optAt = offset + 12 + rdlength;
  if (address > 0) {
    writeOpt(view, optAt, payload);
    return response;
  }

  view.setUint16(offset + 12, 0xc00c);
  view.setUint16(offset + 14, 0xc00c);
  view.setUint32(offset + 16, 1);
  view.setUint32(offset + 20, 3600);
  view.setUint32(offset + 24, 600);
  view.setUint32(offset + 28, 86400);
  view.setUint32(offset + 32, ttl);
  writeOpt(view, optAt, payload);
  return response;
}

function writeOpt(view, offset, payload) {
  if (payload === 0) return;
  view.setUint8(offset, 0);
  view.setUint16(offset + 1, TYPE_OPT);
  view.setUint16(offset + 3, payload);
  view.setUint32(offset + 5, 0);
  view.setUint16(offset + 9, 0);
}

const OPTION_ECS = 8;

export function stripClientSubnet(message, question) {
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  if (view.getUint16(6) !== 0 || view.getUint16(8) !== 0 || view.getUint16(10) === 0) return message;
  const at = question.end;
  if (at + OPT_LENGTH > message.length || message[at] !== 0 || view.getUint16(at + 1) !== TYPE_OPT) return message;

  const rdlength = view.getUint16(at + 9);
  const rdata = at + OPT_LENGTH;
  const rdataEnd = rdata + rdlength;
  if (rdataEnd > message.length) return message;

  const keep = [];
  let cursor = rdata;
  let found = false;
  while (cursor + 4 <= rdataEnd) {
    const length = view.getUint16(cursor + 2);
    if (cursor + 4 + length > rdataEnd) return message;
    if (view.getUint16(cursor) === OPTION_ECS) found = true;
    else keep.push([cursor, 4 + length]);
    cursor += 4 + length;
  }
  if (!found) return message;

  const kept = keep.reduce((total, [, length]) => total + length, 0);
  const out = new Uint8Array(message.length - (rdlength - kept));
  out.set(message.subarray(0, rdata), 0);
  let write = rdata;
  for (const [from, length] of keep) {
    out.set(message.subarray(from, from + length), write);
    write += length;
  }
  out.set(message.subarray(rdataEnd), write);
  new DataView(out.buffer).setUint16(at + 9, kept);
  return out;
}

export function servfail(message) {
  const response = new Uint8Array(Math.max(12, message.length));
  response.set(message.subarray(0, Math.max(12, message.length)));
  response[2] = 0x80 | (message[2] & 0x01);
  response[3] = 0x82;
  return response;
}

export function base64UrlDecode(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function readName(message, start) {
  const labels = [];
  let offset = start;
  let jumps = 0;
  while (offset < message.length) {
    const length = message[offset];
    if (length === 0) return labels.join(".").toLowerCase();
    if ((length & 0xc0) === 0xc0) {
      if (offset + 1 >= message.length || jumps > 8) return "";
      offset = ((length & 0x3f) << 8) | message[offset + 1];
      jumps += 1;
      continue;
    }
    if (length > 63 || offset + length + 1 > message.length) return "";
    labels.push(String.fromCharCode(...message.subarray(offset + 1, offset + 1 + length)));
    offset += length + 1;
  }
  return "";
}

function walkRecords(message, visit, withAuthority) {
  const question = readQuestion(message);
  if (!question) return;
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const records = view.getUint16(6) + (withAuthority ? view.getUint16(8) : 0);
  let offset = question.end;
  for (let index = 0; index < records && offset + 12 <= message.length; index += 1) {
    while (offset < message.length) {
      const length = message[offset];
      if (length === 0) {
        offset += 1;
        break;
      }
      if ((length & 0xc0) === 0xc0) {
        offset += 2;
        break;
      }
      offset += length + 1;
    }
    if (offset + 10 > message.length) return;
    const type = view.getUint16(offset);
    const length = view.getUint16(offset + 8);
    if (offset + 10 + length > message.length) return;
    visit(type, offset, view, length);
    offset += 10 + length;
  }
}

export function cnameTargets(message) {
  const targets = [];
  walkRecords(
    message,
    (type, offset) => {
      if (type !== TYPE_CNAME) return;
      const target = readName(message, offset + 10);
      if (target) targets.push(target);
    },
    false
  );
  return targets;
}

export function decrementTtl(message, seconds) {
  if (seconds <= 0) return message;
  walkRecords(
    message,
    (type, offset, view) => {
      const ttl = view.getUint32(offset + 4);
      view.setUint32(offset + 4, ttl > seconds ? ttl - seconds : 1);
    },
    true
  );
  return message;
}

export function setTtl(message, ttl) {
  walkRecords(message, (type, offset, view) => view.setUint32(offset + 4, ttl), true);
  return message;
}

export function boostTtl(message, floor) {
  walkRecords(
    message,
    (type, offset, view) => {
      if (view.getUint32(offset + 4) < floor) view.setUint32(offset + 4, floor);
    },
    true
  );
  return message;
}

export function minimumTtl(message) {
  let ttl = Infinity;
  walkRecords(
    message,
    (type, offset, view) => {
      ttl = Math.min(ttl, view.getUint32(offset + 4));
    },
    false
  );
  if (Number.isFinite(ttl)) return ttl;
  walkRecords(
    message,
    (type, offset, view, length) => {
      if (type === TYPE_SOA && length >= 22) ttl = Math.min(ttl, view.getUint32(offset + 4), view.getUint32(offset + 6 + length));
    },
    true
  );
  return Number.isFinite(ttl) ? ttl : 0;
}

const FLAG_CD = 0x10;
const FLAG_DO = 0x8000;

export function queryVariant(message, question) {
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const checkingDisabled = (message[3] & FLAG_CD) !== 0;
  const at = question.end;
  const opt = view.getUint16(10) > 0 && at + OPT_LENGTH <= message.length && message[at] === 0 && view.getUint16(at + 1) === TYPE_OPT;
  const dnssecOk = opt && (view.getUint16(at + 7) & FLAG_DO) !== 0;
  return `${opt ? 1 : 0}${dnssecOk ? 1 : 0}${checkingDisabled ? 1 : 0}`;
}

const opcode = (message) => (message[2] >> 3) & 0x0f;

export function answersQuery(query, response) {
  if (response.length < 12 || (response[2] & 0x80) === 0) return false;
  if (response[0] !== query[0] || response[1] !== query[1] || opcode(response) !== opcode(query)) return false;
  const asked = readQuestion(query);
  const answered = readQuestion(response);
  return Boolean(asked && answered && asked.name === answered.name && asked.type === answered.type && asked.class === answered.class);
}
