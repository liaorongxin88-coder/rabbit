export function hasAtMostDecimalPlaces(value: number, maximum: number) {
  if (!Number.isFinite(value) || !Number.isInteger(maximum) || maximum < 0) {
    return false;
  }
  const [coefficient, exponentText] = value.toString().toLowerCase().split("e");
  const decimalPoint = coefficient.indexOf(".");
  const fractionLength =
    decimalPoint === -1 ? 0 : coefficient.length - decimalPoint - 1;
  const exponent = exponentText ? Number(exponentText) : 0;
  return Math.max(0, fractionLength - exponent) <= maximum;
}
