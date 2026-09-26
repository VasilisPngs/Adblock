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
const FLAG_CD = 0x10;
const FLAG_DO = 0x8000;
const OPTION_PADDING = 12;
const OPTION_EDE = 15;
const PADDING_BLOCK = 468;
const RESPONSE_PAYLOAD = 1232;
const MAX_MESSAGE = 65535;
export const EXTENDED_BADVERS = 1;

export function blockedResponse(message, question, ttl) {
  const questionBytes = message.subarray(12, question.end);
  const address = question.type === TYPE_A ? 4 : question.type === TYPE_AAAA ? 16 : 0;
  const rdlength = address > 0 ? address : SOA_RDLENGTH;
  const response = new Uint8Array(12 + questionBytes.length + 12 + rdlength);
  const view = new DataView(response.buffer);

  response.set(message.subarray(0, 2), 0);
  response[2] = 0x80 | (message[2] & 0x79);
  response[3] = 0x80;
  view.setUint16(4, 1);
  view.setUint16(6, address > 0 ? 1 : 0);
  view.setUint16(8, address > 0 ? 0 : 1);
  response.set(questionBytes, 12);

  const offset = 12 + questionBytes.length;
  view.setUint16(offset, 0xc00c);
  view.setUint16(offset + 2, address > 0 ? question.type : TYPE_SOA);
  view.setUint16(offset + 4, question.class);
  view.setUint32(offset + 6, ttl);
  view.setUint16(offset + 10, rdlength);
  if (address > 0) return response;

  view.setUint16(offset + 12, 0xc00c);
  view.setUint16(offset + 14, 0xc00c);
  view.setUint32(offset + 16, 1);
  view.setUint32(offset + 20, 3600);
  view.setUint32(offset + 24, 600);
  view.setUint32(offset + 28, 86400);
  view.setUint32(offset + 32, ttl);
  return response;
}

function bareResponse(message, question, rcode) {
  const response = new Uint8Array(question.end);
  response.set(message.subarray(0, question.end));
  response[2] = 0x80 | (message[2] & 0x79);
  response[3] = 0x80 | rcode;
  const view = new DataView(response.buffer);
  view.setUint16(6, 0);
  view.setUint16(8, 0);
  view.setUint16(10, 0);
  return response;
}

export const servfail = (message, question) => bareResponse(message, question, 2);

export const badVersion = (message, question) => bareResponse(message, question, 0);

export function normalizeQuery(message, question) {
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const client = { edns: false, version: 0, dnssecOk: false, checkingDisabled: (message[3] & FLAG_CD) !== 0, padding: false };
  const at = question.end;
  if (view.getUint16(10) > 0 && at + OPT_LENGTH <= message.length && message[at] === 0 && view.getUint16(at + 1) === TYPE_OPT) {
    client.edns = true;
    client.version = message[at + 6];
    client.dnssecOk = (view.getUint16(at + 7) & FLAG_DO) !== 0;
    const rdataEnd = Math.min(message.length, at + OPT_LENGTH + view.getUint16(at + 9));
    for (let cursor = at + OPT_LENGTH; cursor + 4 <= rdataEnd; ) {
      const length = view.getUint16(cursor + 2);
      if (cursor + 4 + length > rdataEnd) break;
      if (view.getUint16(cursor) === OPTION_PADDING) client.padding = true;
      cursor += 4 + length;
    }
  }
  const forward = new Uint8Array(at + OPT_LENGTH);
  forward.set(message.subarray(0, at), 0);
  const out = new DataView(forward.buffer);
  out.setUint16(6, 0);
  out.setUint16(8, 0);
  out.setUint16(10, 1);
  out.setUint16(at + 1, TYPE_OPT);
  out.setUint16(at + 3, RESPONSE_PAYLOAD);
  out.setUint32(at + 5, client.dnssecOk ? FLAG_DO : 0);
  out.setUint16(at + 9, 0);
  return { forward, client };
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

const ANSWERS = 1;
const WITH_AUTHORITY = 2;
const ALL_RECORDS = 3;

function walkRecords(message, visit, sections) {
  const question = readQuestion(message);
  if (!question) return;
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const records = view.getUint16(6) + (sections > 1 ? view.getUint16(8) : 0) + (sections > 2 ? view.getUint16(10) : 0);
  let offset = question.end;
  for (let index = 0; index < records && offset + 11 <= message.length; index += 1) {
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
    ANSWERS
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
    WITH_AUTHORITY
  );
  return message;
}

export function setTtl(message, ttl) {
  walkRecords(message, (type, offset, view) => view.setUint32(offset + 4, ttl), WITH_AUTHORITY);
  return message;
}

export function boostTtl(message, floor) {
  walkRecords(
    message,
    (type, offset, view) => {
      if (view.getUint32(offset + 4) < floor) view.setUint32(offset + 4, floor);
    },
    WITH_AUTHORITY
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
    ANSWERS
  );
  if (Number.isFinite(ttl)) return ttl;
  walkRecords(
    message,
    (type, offset, view, length) => {
      if (type === TYPE_SOA && length >= 22) ttl = Math.min(ttl, view.getUint32(offset + 4), view.getUint32(offset + 6 + length));
    },
    WITH_AUTHORITY
  );
  return Number.isFinite(ttl) ? ttl : 0;
}

const opcode = (message) => (message[2] >> 3) & 0x0f;

export function answersQuery(query, response) {
  if (response.length < 12 || (response[2] & 0x80) === 0) return false;
  if (response[0] !== query[0] || response[1] !== query[1] || opcode(response) !== opcode(query)) return false;
  const asked = readQuestion(query);
  const answered = readQuestion(response);
  return Boolean(asked && answered && asked.name === answered.name && asked.type === answered.type && asked.class === answered.class);
}

function optTypeOffset(message) {
  let found = -1;
  walkRecords(
    message,
    (type, offset) => {
      if (type === TYPE_OPT && found < 0) found = offset;
    },
    ALL_RECORDS
  );
  return found;
}

export function extendedRcode(message) {
  const opt = optTypeOffset(message);
  return opt < 0 ? 0 : message[opt + 4];
}

export function edeOptions(message) {
  const opt = optTypeOffset(message);
  if (opt < 0) return [];
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const rdataEnd = opt + 10 + view.getUint16(opt + 8);
  const found = [];
  for (let cursor = opt + 10; cursor + 4 <= rdataEnd; ) {
    const length = view.getUint16(cursor + 2);
    if (cursor + 4 + length > rdataEnd) break;
    if (view.getUint16(cursor) === OPTION_EDE) found.push(message.slice(cursor, cursor + 4 + length));
    cursor += 4 + length;
  }
  return found;
}

export function withoutOpt(message) {
  const opt = optTypeOffset(message);
  if (opt < 1 || message[opt - 1] !== 0) return message;
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const start = opt - 1;
  const end = opt + 10 + view.getUint16(opt + 8);
  const out = new Uint8Array(message.length - (end - start));
  out.set(message.subarray(0, start), 0);
  out.set(message.subarray(end), start);
  const outView = new DataView(out.buffer);
  outView.setUint16(10, outView.getUint16(10) - 1);
  return out;
}

export function respond(body, client, extended = 0, options = []) {
  if (!client.edns) return body;
  const optionBytes = options.reduce((total, option) => total + option.length, 0);
  let size = body.length + OPT_LENGTH + optionBytes;
  if (size > MAX_MESSAGE) return body;
  let fill = -1;
  if (client.padding) {
    const target = Math.min(MAX_MESSAGE, Math.ceil((size + 4) / PADDING_BLOCK) * PADDING_BLOCK);
    if (target >= size + 4) {
      fill = target - size - 4;
      size = target;
    }
  }
  const out = new Uint8Array(size);
  out.set(body, 0);
  const view = new DataView(out.buffer);
  view.setUint16(10, view.getUint16(10) + 1);
  let at = body.length;
  view.setUint16(at + 1, TYPE_OPT);
  view.setUint16(at + 3, RESPONSE_PAYLOAD);
  view.setUint8(at + 5, extended);
  view.setUint16(at + 7, client.dnssecOk ? FLAG_DO : 0);
  view.setUint16(at + 9, optionBytes + (fill >= 0 ? 4 + fill : 0));
  at += OPT_LENGTH;
  for (const option of options) {
    out.set(option, at);
    at += option.length;
  }
  if (fill >= 0) {
    view.setUint16(at, OPTION_PADDING);
    view.setUint16(at + 2, fill);
  }
  return out;
}
