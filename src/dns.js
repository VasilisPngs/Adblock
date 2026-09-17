const TYPE_A = 1;
const TYPE_AAAA = 28;
const RCODE_NXDOMAIN = 3;

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
  if (view.getUint16(4) === 0) return null;
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

export function blockedResponse(message, question, mode, ttl) {
  const questionBytes = message.subarray(12, question.end);
  const answerable = mode === "zero" && (question.type === TYPE_A || question.type === TYPE_AAAA);
  const address = question.type === TYPE_A ? 4 : 16;
  const answerLength = answerable ? questionBytes.length - 4 + 10 + address : 0;
  const response = new Uint8Array(12 + questionBytes.length + answerLength);
  const view = new DataView(response.buffer);

  response.set(message.subarray(0, 2), 0);
  const recursionDesired = message[2] & 0x01;
  response[2] = 0x80 | recursionDesired;
  response[3] = answerable ? 0x80 : 0x80 | RCODE_NXDOMAIN;
  view.setUint16(4, 1);
  view.setUint16(6, answerable ? 1 : 0);
  view.setUint16(8, 0);
  view.setUint16(10, 0);
  response.set(questionBytes, 12);

  if (answerable) {
    let offset = 12 + questionBytes.length;
    response.set(questionBytes.subarray(0, questionBytes.length - 4), offset);
    offset += questionBytes.length - 4;
    view.setUint16(offset, question.type);
    view.setUint16(offset + 2, question.class);
    view.setUint32(offset + 4, ttl);
    view.setUint16(offset + 8, address);
  }

  return response;
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

export function minimumTtl(message) {
  const question = readQuestion(message);
  if (!question) return 0;
  const view = new DataView(message.buffer, message.byteOffset, message.byteLength);
  const answers = view.getUint16(6);
  if (answers === 0) return 0;
  let offset = question.end;
  let ttl = Infinity;
  for (let index = 0; index < answers && offset + 12 <= message.length; index += 1) {
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
    if (offset + 10 > message.length) break;
    ttl = Math.min(ttl, view.getUint32(offset + 4));
    offset += 10 + view.getUint16(offset + 8);
  }
  return Number.isFinite(ttl) ? ttl : 0;
}
