function dumpAllMethods(obj) {
  console.log("=== Dynamic Method Dump ===");

  if (obj === null || obj === undefined) {
    console.log("Error: Cannot dump methods on null or undefined.");
    console.log("===========================");
    return;
  }

  // Check if it's a Java object by looking for a getClass method or standard signatures
  var isJavaObject = false;
  try {
    isJavaObject = (typeof obj.getClass === 'function' || (obj && obj.class && typeof obj.class.getMethods === 'function'));
  } catch (e) {
    isJavaObject = false;
  }

  if (isJavaObject) {
    try {
      // Use Java Reflection to bypass Rhino wrapper limitations
      var javaClass = typeof obj.getClass === 'function' ? obj.getClass() : obj.class;
      var methods = javaClass.getMethods(); // Gets all public methods including inherited ones
      var seenMethods = {};

      var className = javaClass.getName();
      console.log("Detected Java Object: " + className);

      for (var i = 0; i < methods.length; i++) {
        var method = methods[i];
        var methodName = method.getName();

        if (!seenMethods[methodName]) {
          seenMethods[methodName] = true;
          
          // Get the declaring class name to show exactly where the method originates
          var declaringClassName = method.getDeclaringClass().getName();
          
          console.log("Method: " + methodName + "() -> Found at: " + declaringClassName);
        }
      }
    } catch (javaError) {
      console.log("Failed to inspect Java methods via reflection: " + javaError);
    }
  } else {
    // Standard JavaScript fallback for native objects, arrays, and configs
    var currentObj = obj;
    var seenJSMethods = {};
    var chainIndex = 0;

    while (currentObj) {
      var jsTypeName = "Object";
      try {
        var strRep = Object.prototype.toString.call(currentObj);
        var match = strRep.match(/\s([a-zA-Z0-9_]+)/);
        if (match) jsTypeName = match[1];
      } catch (e) {}

      var pathLabel = "[" + chainIndex + "] " + jsTypeName;
      var props = [];

      try {
        props = Object.getOwnPropertyNames(currentObj);
      } catch (e) {
        // Fallback for simple loops if getOwnPropertyNames fails
        for (var key in currentObj) { props.push(key); }
      }

      for (var j = 0; j < props.length; j++) {
        var propName = props[j];
        if (propName === 'constructor') continue;

        try {
          if (typeof obj[propName] === 'function') {
            if (!seenJSMethods[propName]) {
              seenJSMethods[propName] = true;
              console.log("Method: " + propName + " -> Found at: " + pathLabel);
            }
          }
        } catch (e) {}
      }

      try {
        var nextProto = Object.getPrototypeOf ? Object.getPrototypeOf(currentObj) : currentObj.__proto__;
        if (!nextProto || nextProto === currentObj) break;
        currentObj = nextProto;
      } catch (e) {
        break;
      }
      chainIndex++;
    }
  }
  
  console.log("===========================");
}

const LSO_API = Java.loadClass("sfiomn.legendarysurvivaloverhaul.common.blocks");
// const LSO_UTIL = Java.loadClass("sfiomn.legendarysurvivaloverhaul.util");
dumpAllMethods(LSO_API);
// dumpAllMethods(LSO_UTIL);
