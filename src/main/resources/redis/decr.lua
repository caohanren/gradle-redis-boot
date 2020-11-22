local packetNum = KEYS[1]
local packetAmount = KEYS[2]
local randomAmount = KEYS[3]

-- setnx info
local result_one = redis.call('DECR',packetNum)
if result_one >= 0
then
local result_two = redis.call('DECRBY',packetAmount,randomAmount)
return result_two
else
return result_one
end