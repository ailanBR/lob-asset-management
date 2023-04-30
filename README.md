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

### 2023-04-25
- Add portfolio file (read/write)
- Add schema function validation
- ReFactory io functions
- Create delete file function

### 2023-04-26
- Read only new files function
- Add support to upsert a simple map


### 2023-04-28
- Started Read B3 prices
- Create a secrets file
- Create relevant namespace for global config and secrets


## TODO

1. Read B3 prices
2. Turn solution on API or CronJob
3. Get market price
4. Register profit by asset (Dividend, JCP...)
5. Create percentage by Category/Asset
6. Regiter new historical price in asset

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
