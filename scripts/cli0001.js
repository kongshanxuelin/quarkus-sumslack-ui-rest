(function(){
    /**
     * 统计主表 Mock（ES5）：仅按 pageSize / pageNum 分页
     *
     * @param {Object} param
     * @param {number} param.pageSize 每页条数
     * @param {number} param.pageNum 页码（从 0 开始）
     * @returns {{list: Object[], total: number, pageNum: number, pageSize: number}}
     */
    function fetchStatPrimaryMock(param) {
        var REGION_NAMES = [
            '北京',
            '上海',
            '广东',
            '江苏',
            '浙江',
            '山东',
            '四川',
            '湖北',
            '湖南',
            '福建',
            '安徽',
            '河南',
            '河北',
            '陕西',
            '重庆',
            '天津',
            '辽宁',
            '江西',
            '云南',
            '广西',
        ];

        if (!fetchStatPrimaryMock._datasetCache) {
            fetchStatPrimaryMock._datasetCache = null;
        }

        function randomInt(min, max) {
            return Math.floor(Math.random() * (max - min + 1)) + min;
        }

        function randomFloat(min, max, digits) {
            if (digits === undefined) {
                digits = 2;
            }
            return Number((Math.random() * (max - min) + min).toFixed(digits));
        }

        function formatCode(index) {
            var num = index + 1;
            var str = String(num);
            while (str.length < 5) {
                str = '0' + str;
            }
            return 'S' + str;
        }

        function buildStatPrimaryRow(index) {
            var region = REGION_NAMES[index % REGION_NAMES.length];
            var totalIssueAmount = randomFloat(50000, 5000000, 2);
            var issueCount = randomInt(10, 500);
            var totalRepayment = randomFloat(30000, totalIssueAmount * 0.9, 2);
            var totalNetFinancing = Number(
                (totalIssueAmount - totalRepayment).toFixed(2),
            );

            return {
                region: region,
                totalIssueAmount: totalIssueAmount,
                issueCount: issueCount,
                issueYoyChangeScale: randomFloat(-500000, 800000, 2),
                issueYoyChangeRatio: randomFloat(-35, 45, 2),
                issueMomChangeScale: randomFloat(-200000, 300000, 2),
                issueMomChangeRatio: randomFloat(-20, 25, 2),
                weightedAvgCoupon: randomFloat(2.5, 6.8, 2),
                totalRepayment: totalRepayment,
                repaymentCount: randomInt(5, issueCount),
                repaymentYoyChangeScale: randomFloat(-400000, 600000, 2),
                repaymentYoyChangeRatio: randomFloat(-30, 40, 2),
                actualEarlyRedemption: randomFloat(0, totalRepayment * 0.3, 2),
                actualPutBack: randomFloat(0, totalRepayment * 0.25, 2),
                actualRedemption: randomFloat(0, totalRepayment * 0.2, 2),
                totalNetFinancing: totalNetFinancing,
                netFinancingYoyChangeScale: randomFloat(-300000, 500000, 2),
                netFinancingYoyChangeRatio: randomFloat(-40, 50, 2),
                shareOfTotalIssue: randomFloat(0.5, 15, 2),
            };
        }

        function getOrCreateDataset() {
            if (fetchStatPrimaryMock._datasetCache) {
                return fetchStatPrimaryMock._datasetCache;
            }

            var total = 999;
            var rows = [];
            var i;

            for (i = 0; i < total; i++) {
                rows.push(buildStatPrimaryRow(i));
            }

            fetchStatPrimaryMock._datasetCache = rows;
            return rows;
        }

        var pageSize = param.pageSize;
        var pageNum = param.pageNum;
        var safePageSize = Math.max(1, Number(pageSize) || 1);
        var safePageNum = Number(pageNum);
        if (isNaN(safePageNum) || safePageNum < 0) {
            safePageNum = 0;
        }
        var rows = getOrCreateDataset();
        var total = rows.length;
        var start = safePageNum * safePageSize;
        var pageRows = rows.slice(start, start + safePageSize);
        var list = [];
        var i;
        var key;

        for (i = 0; i < pageRows.length; i++) {
            var src = pageRows[i];
            var dest = {};
            for (key in src) {
                if (src.hasOwnProperty(key)) {
                    dest[key] = src[key];
                }
            }
            dest.code = formatCode(start + i);
            list.push(dest);
        }

        return {
            list: list,
            total: total,
            pageNum: safePageNum,
            pageSize: safePageSize,
        };
    }

    return fetchStatPrimaryMock(params);

})()