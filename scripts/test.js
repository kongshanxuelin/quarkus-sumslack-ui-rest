(function(){
    // db.createTable("test", "id INTEGER PRIMARY KEY, name TEXT, value INTEGER");
    // db.insert("test", {name: "hello", value: params.a});
    // db.insert("test", {name: "world", value: params.b});
    db.updateRecords("test",{"name":"xxxx测试"},"id=2");
    return db.query("SELECT * FROM test");
})()