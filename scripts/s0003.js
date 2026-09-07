(function(){
    // storybook 中 DataBridge 的模拟 Table 数据（ES5）
    function randomInt(min, max) {
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }

    function randomName() {
        var chars =
            "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥天地人和风云雷雨山水木火土日月星辰江河湖海";
        var len = randomInt(2, 4);
        var s = "";
        var j;
        for (j = 0; j < len; j++) {
            s += chars.charAt(randomInt(0, chars.length - 1));
        }
        return s;
    }

    var ROW_COUNT = 20;
    var result = [];
    var i;

    for (i = 0; i < ROW_COUNT; i++) {
        result.push({
            id: '第' + params.pageNum + "页-" + String(randomInt(10000, 99999)),
            name: randomName(),
            age: randomInt(18, 60),
        });
    }

    return result;
})()