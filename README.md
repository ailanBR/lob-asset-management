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

1. Store data on cloud
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
2. Add a way to manual insert information (Front-end proj?)
   1. Function to read stored file and add one row
3. [Documentation] Document the transaction types and what happening
4. [Market] Don't get market price for stocks on weekends/holiday
5. [Http_in] Find another option for Alpha API / Web Scraping
6. [Market] Count API calls
7. Consider subscription events

## FIXME
1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)
2. Set telegram key as environment variable

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
