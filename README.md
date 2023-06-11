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

1. Turn solution on CronJob (Doing)
2. Store data on cloud
   1. [] AWS
   2. [] Firebase (Doing)
   3. [] Other
3. Add a way to manual insert information (Front-end proj?)
   1. Function to read the file and add one row
4. Document the transaction types and what happening
5. Find another option for Alpha API

## FIXME
1. Consider incorporation's in the portfolio process (BIDI11 / HAPV3 / LINX3)
2. Consider desdobramento in the portfolio process (OIBR3)

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
