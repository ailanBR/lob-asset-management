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

## Change Log

### 2023-03-25 
- Project created
- Read xmlx files
- Data transformation for
  - Asset
  - Transaction

### 2023-03-31
- Read and write edn files
- Backup file before write new file

### 2023-04-01
- Read the b3 movement and write edn file for
  - asset
  - transaction
- Filter asset to be unique records
- Concat stored file data with read from b3 movement
- Remove duplicated when concat stored with b3 movement
- New folders
  - controller
  - logic

### 2023-04-18
- Validate duplicated transactions
- DR 001 - Unique by ID and don't treat update/changes in the comparative

### 2023-04-21
- Transaction to portfolio average price calculation
- Concluded transaction to portfolio adepter

###

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
