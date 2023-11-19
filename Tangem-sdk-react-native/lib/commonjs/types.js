"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.LinkedTerminalStatus = exports.Status = exports.FileVisibility = exports.FirmwareType = exports.EncryptionMode = exports.SigningMethod = exports.EllipticCurve = void 0;

/**
 * Elliptic curve used for wallet key operations.
 */
let EllipticCurve;
exports.EllipticCurve = EllipticCurve;

(function (EllipticCurve) {
  EllipticCurve["Secp256k1"] = "secp256k1";
  EllipticCurve["Ed25519"] = "ed25519";
  EllipticCurve["Secp256r1"] = "secp256r1";
})(EllipticCurve || (exports.EllipticCurve = EllipticCurve = {}));

let SigningMethod;
exports.SigningMethod = SigningMethod;

(function (SigningMethod) {
  SigningMethod["SignHash"] = "SignHash";
  SigningMethod["SignRaw"] = "SignRaw";
  SigningMethod["SignHashSignedByIssuer"] = "SignHashSignedByIssuer";
  SigningMethod["SignRawSignedByIssuer"] = "SignRawSignedByIssuer";
  SigningMethod["SignHashSignedByIssuerAndUpdateIssuerData"] = "SignHashSignedByIssuerAndUpdateIssuerData";
  SigningMethod["SignRawSignedByIssuerAndUpdateIssuerData"] = "SignRawSignedByIssuerAndUpdateIssuerData";
  SigningMethod["SignPos"] = "SignPos";
})(SigningMethod || (exports.SigningMethod = SigningMethod = {}));

let EncryptionMode;
exports.EncryptionMode = EncryptionMode;

(function (EncryptionMode) {
  EncryptionMode["None"] = "none";
  EncryptionMode["Fast"] = "fast";
  EncryptionMode["Strong"] = "strong";
})(EncryptionMode || (exports.EncryptionMode = EncryptionMode = {}));

let FirmwareType;
exports.FirmwareType = FirmwareType;

(function (FirmwareType) {
  FirmwareType["Sdk"] = "d SDK";
  FirmwareType["Release"] = "r";
  FirmwareType["Special"] = "special";
})(FirmwareType || (exports.FirmwareType = FirmwareType = {}));

let FileVisibility;
exports.FileVisibility = FileVisibility;

(function (FileVisibility) {
  FileVisibility["Private"] = "private";
  FileVisibility["Public"] = "public";
})(FileVisibility || (exports.FileVisibility = FileVisibility = {}));

let Status;
exports.Status = Status;

(function (Status) {
  Status["Failed"] = "failed";
  Status["Warning"] = "warning";
  Status["Skipped"] = "skipped";
  Status["VerifiedOffline"] = "verifiedOffline";
  Status["Verified"] = "verified";
})(Status || (exports.Status = Status = {}));

let LinkedTerminalStatus;
exports.LinkedTerminalStatus = LinkedTerminalStatus;

(function (LinkedTerminalStatus) {
  LinkedTerminalStatus["Current"] = "current";
  LinkedTerminalStatus["Other"] = "other";
  LinkedTerminalStatus["None"] = "none";
})(LinkedTerminalStatus || (exports.LinkedTerminalStatus = LinkedTerminalStatus = {}));
//# sourceMappingURL=types.js.map