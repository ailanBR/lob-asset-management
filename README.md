# Lob asset management

A solution created to generate investments income tax release 

## Usage

### Read XLSX movements

Read a XLSX document and turn in EDN entities:

 - Asset
 - Transaction
 - Portfolio

### Asset
#### Asset information

- Price
- Profit/Loss
- Portfolio percentage1
- Purchase quantity media

### Portfolio
#### Portfolio information

- Average price
- Total cost
- Profit from dividend's
- Allocation percentage

### Portfolio Configuration [TODO]

Provide where asset need to be purchase and how much

## TODO

1. Turn solution on ~~CronJob~~ Poller (Doing)
   1. [x] Polling function
   2. [x] Polling process
   3. [ ] Fix read files (Process folder executing from terminal)
2. Store data on cloud
   1. [ ] AWS
      1. ~~Thinking about the item 4. maybe we can use S3 only for the in-data files~~
   2. [ ] Firebase (Doing)
      1. [x] Find firebase library (alekcz/fire)
      2. [x] Create firebase project 
      3. [ ] Fix connection only works in terminal (lein run)
      4. [ ] Create write/read documents
   3. [ ] Do nothing
      1. Solution works for read/write entities in terminal
      2. ~~The problem is only **maybe** in the in-data files~~
3. Add a way to manual insert information (Front-end proj?)
   1. Function to read stored file and add one row
4. [Documentation] Document the transaction types and what happening
5. [Market] Don't get market price for stocks on weekends/holiday
6. [Portfolio] Compare Gain/Loss with days ago
   1. [x] D-1
   2. [ ] Week
   3. [ ] Month
   4. [ ] Year
7. [Http_in] Find another option for Alpha API
8. [Telegram] Send periodic telegram message
9. [Market] Count API calls

## FIXME
1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)
2. Consider desdobramento in the portfolio process (OIBR3)
3. Remove asset/id field to use only asset/ticket
4. Set telegram key as environment variable

## License

Copyright © 2023 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
