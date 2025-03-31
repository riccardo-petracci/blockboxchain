// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.5.0 <0.9.0;


contract HStorage 
{

    address[] private owners;
    mapping(string => uint) private json_dump_hash;

    constructor()
    {
        owners.push(msg.sender);   
    }

    function addOwner(address newOwner) public onlyOwners
    {
        owners.push(newOwner);   
    }

    function store_hash(string memory jdh) public onlyOwners
    {
        json_dump_hash[jdh] = block.number;
    }

    function retrieve(string memory jdh) public view returns (uint bn){
        bn = json_dump_hash[jdh];
    }

    modifier onlyOwners
    {
        bool check = false;
        for (uint i=0; i<owners.length; i++) 
        {
            if(msg.sender == owners[i])
            {
                check = true;
                break;
            }
        }

        require(check);
        _;
    }
}






