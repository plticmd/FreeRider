/**
 * Elliptic curve used for wallet key operations.
 */
export let EllipticCurve;

(function (EllipticCurve) {
  EllipticCurve["Secp256k1"] = "secp256k1";
  EllipticCurve["Ed25519"] = "ed25519";
  EllipticCurve["Secp256r1"] = "secp256r1";
})(EllipticCurve || (EllipticCurve = {}));

export let SigningMethod;

(function (SigningMethod) {
  SigningMethod["SignHash"] = "SignHash";
  SigningMethod["SignRaw"] = "SignRaw";
  SigningMethod["SignHashSignedByIssuer"] = "SignHashSignedByIssuer";
  SigningMethod["SignRawSignedByIssuer"] = "SignRawSignedByIssuer";
  SigningMethod["SignHashSignedByIssuerAndUpdateIssuerData"] = "SignHashSignedByIssuerAndUpdateIssuerData";
  SigningMethod["SignRawSignedByIssuerAndUpdateIssuerData"] = "SignRawSignedByIssuerAndUpdateIssuerData";
  SigningMethod["SignPos"] = "SignPos";
})(SigningMethod || (SigningMethod = {}));

export let EncryptionMode;

(function (EncryptionMode) {
  EncryptionMode["None"] = "none";
  EncryptionMode["Fast"] = "fast";
  EncryptionMode["Strong"] = "strong";
})(EncryptionMode || (EncryptionMode = {}));

export let FirmwareType;

(function (FirmwareType) {
  FirmwareType["Sdk"] = "d SDK";
  FirmwareType["Release"] = "r";
  FirmwareType["Special"] = "special";
})(FirmwareType || (FirmwareType = {}));

export let FileVisibility;

(function (FileVisibility) {
  FileVisibility["Private"] = "private";
  FileVisibility["Public"] = "public";
})(FileVisibility || (FileVisibility = {}));

export let Status;

(function (Status) {
  Status["Failed"] = "failed";
  Status["Warning"] = "warning";
  Status["Skipped"] = "skipped";
  Status["VerifiedOffline"] = "verifiedOffline";
  Status["Verified"] = "verified";
})(Status || (Status = {}));

export let LinkedTerminalStatus;

(function (LinkedTerminalStatus) {
  LinkedTerminalStatus["Current"] = "current";
  LinkedTerminalStatus["Other"] = "other";
  LinkedTerminalStatus["None"] = "none";
})(LinkedTerminalStatus || (LinkedTerminalStatus = {}));
//# sourceMappingURL=types.js.map