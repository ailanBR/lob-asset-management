# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
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

### 2023-04-30
- Add percentage by asset in portfolio
- Add category field in portfolio
- Add percentage by category

### 2023-05-02
- Register profit by asset (Dividend, JCP...)

### 2023-05-03
- Create Market controller to get asset prices

### 2023-05-04
- Include get less updated asset
- Update file with last market price
- Fix get only new releases process

### 2023-05-06
### 2023-05-07
### 2023-05-08
- Fix process file flow
- Fix filter for assets and transactions
- Create Polling function

### 2023-05-10
- Create a file with fixed asset information
- Started creation of irpf release
- Add get asset overview endpoint [don't have b3 asset info]

### 2023-05-11
- Add fixed asset info in asset
- Add allow alpha api asset in fixed info file
- Change way to get asset category to fixed asset info
- Create function to format br tax document

### 2023-05-12
- Fix release description
- Read all data from B3 with success with some fixes in asset and transaction adapters

## [0.1.1] - 2023-03-25
### Changed

### Removed

### Fixed

## 0.1.0 - 2023-03-25
### Added

[Unreleased]: https://github.com/your-name/lob-asset-management/compare/0.1.1...HEAD
[0.1.1]: https://github.com/your-name/lob-asset-management/compare/0.1.0...0.1.1
