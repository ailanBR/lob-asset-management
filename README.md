# lob-asset-management

A system to provide asset information's to management using functional programming with Clojure.

## Usage

### Import asset movements

Read a XLSX document and turn in EDN fallowing the movement type:

 - Buy
 - Sell
 - Dividend
 - Bonus

(OBS: In the first version only BR stocks)

### Portfolio balance

Provide where asset need to be purchase and how much

## Asset information

- Price
- Profit/Loss
- Portfolio percentage1
- Purchase quantity media

## TODO

1. [IRPF Relase] UPDATE IRPF description
2. [IRPF Relase]Get last year asset price
   1. Register the historical price and if not exist in historic get in the API
2. [IRPF Relase] Get last year total invested
3. Register Alpha API ticket in asset ?
4. Register historical prices in asset
5. Store data on cloud
   1. [] AWS
   2. [] Firebase
   3. [] Other
6. Turn solution on API or CronJob

## DRs

DR 001 - Transaction adapter unique by ID and don't treat update/changes in the comparative
  - Avoid duplicated records by ID
  - Get the most updated data [HOW?]
    - Don't necessary it is the same data 
    - In case of change this flow don't treat it

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
