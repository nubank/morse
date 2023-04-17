// wrapCljFn is used for marshaling data to the JS world.
// cljFn is a Clojure fn that will be called with args
function wrapCljFn(cljFn) {
    var f = function (...args) {
        return cljFn.invoke(...args);
    }
    return f;
};