(function () {
  const globalRef = globalThis as typeof globalThis & {
    global?: typeof globalThis;
  };
  if (!globalRef.global) {
    globalRef.global = globalRef;
  }
})();
