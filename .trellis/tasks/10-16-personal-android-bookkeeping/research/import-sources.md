# Import source research

## Confirmed v1 scope

The user wants CSV/JSON import and export using the app's fixed schema. Files exported by the app must round-trip. Any validation error aborts the entire import without partial writes. Exact duplicate IDs are skipped and summarized. Dedicated parsing for raw personal WeChat Pay or Alipay exports is deferred.

## Source-format research

- The public official WeChat Pay materials found describe merchant reconciliation downloads, including comma-delimited UTF-8 streams and optional GZIP packaging. They do not establish the format of a personal WeChat wallet export.
- The accessible official Alipay consumer help page for downloading income/expense details did not expose format instructions. Other official materials found describe merchant reconciliation files rather than personal consumer exports.
- Since raw WeChat and Alipay parsing is out of scope for v1, those uncertain formats do not block the fixed app-owned CSV/JSON schema.

## Sources

- [WeChat Pay merchant bill download guide](https://pay.wechatpay.cn/doc/v3/merchant/4013071218)
- [WeChat Pay merchant transaction bill fields](https://pay.wechatpay.cn/doc/v3/merchant/4013071249)
- [Alipay consumer help: download income/expense details](https://cshall.alipay.com/lab/help_detail.htm?catId=237801&help_id=212688)
- [Alipay merchant reconciliation overview](https://help.alipay.com/enterprise/knowledgeDetail.htm?knowledgeId=201603401890)
