// Bridge between WebSqlDriver (Kotlin/Wasm) and sql.js.
//
// sql.js keeps the whole database in memory. That is what makes a synchronous driver possible
// in the browser, and it means persistence is ours: after any write, a debounced snapshot of
// the database goes to IndexedDB, and on launch the last snapshot is loaded back.
(function () {
  var SQL_JS_VERSION = '1.13.0';
  var SQL_JS_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/sql.js/' + SQL_JS_VERSION + '/';
  var DB_NAME = 'splits';
  var STORE = 'files';
  var KEY = 'splits.db';
  var SAVE_DELAY_MS = 250;

  var db = null;
  var saveTimer = null;

  function openStore() {
    return new Promise(function (resolve, reject) {
      var request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = function () { request.result.createObjectStore(STORE); };
      request.onsuccess = function () { resolve(request.result); };
      request.onerror = function () { reject(request.error); };
    });
  }

  function readSnapshot() {
    return openStore().then(function (idb) {
      return new Promise(function (resolve, reject) {
        var get = idb.transaction(STORE, 'readonly').objectStore(STORE).get(KEY);
        get.onsuccess = function () { resolve(get.result || null); };
        get.onerror = function () { reject(get.error); };
      });
    });
  }

  function writeSnapshot(bytes) {
    return openStore().then(function (idb) {
      return new Promise(function (resolve, reject) {
        var tx = idb.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put(bytes, KEY);
        tx.oncomplete = function () { resolve(); };
        tx.onerror = function () { reject(tx.error); };
      });
    });
  }

  function saveNow() {
    if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; }
    if (!db) return;
    writeSnapshot(db.export()).catch(function (e) { console.error('Saving database failed', e); });
  }

  function scheduleSave() {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(saveNow, SAVE_DELAY_MS);
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script');
      script.src = src;
      script.onload = resolve;
      script.onerror = function () { reject(new Error('Could not load ' + src)); };
      document.head.appendChild(script);
    });
  }

  function bind(stmt, paramsJson) {
    var params = JSON.parse(paramsJson);
    if (params.length) stmt.bind(params);
  }

  globalThis.splitsSql = {
    init: function () {
      if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});
      return Promise.all([
        loadScript(SQL_JS_BASE + 'sql-wasm.js').then(function () {
          return initSqlJs({ locateFile: function (file) { return SQL_JS_BASE + file; } });
        }),
        readSnapshot().catch(function (e) { console.error('Reading database failed', e); return null; }),
      ]).then(function (results) {
        var SQL = results[0];
        var saved = results[1];
        db = saved ? new SQL.Database(new Uint8Array(saved)) : new SQL.Database();
        db.exec('PRAGMA foreign_keys = ON');
      });
    },

    // Returns the rows as a JSON array of arrays, in column order.
    query: function (sql, paramsJson) {
      var stmt = db.prepare(sql);
      try {
        bind(stmt, paramsJson);
        var rows = [];
        while (stmt.step()) rows.push(stmt.get());
        return JSON.stringify(rows);
      } finally {
        stmt.free();
      }
    },

    // Returns the number of rows changed.
    run: function (sql, paramsJson) {
      var stmt = db.prepare(sql);
      try {
        bind(stmt, paramsJson);
        stmt.step();
      } finally {
        stmt.free();
      }
      scheduleSave();
      return db.getRowsModified();
    },
  };

  // iOS can suspend or kill a backgrounded web app at any moment; flush the pending save first.
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden' && saveTimer) saveNow();
  });
  window.addEventListener('pagehide', function () { if (saveTimer) saveNow(); });
})();
