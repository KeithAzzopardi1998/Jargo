import numpy as np
import sys
np.set_printoptions(threshold=sys.maxsize)
import os
import argparse
import models.CSTN as DEMODEL
import utils.metrics as Metrics
from keras.optimizers import Adam
import tensorflow as tf

def rmse(a, b):
    return Metrics.rmse(a, b) * 287.0 / 2.0
def o_rmse(a, b):
    return Metrics.o_rmse(a, b) * 287.0 / 2.0

metrics = [ rmse, Metrics.mape,  \
            o_rmse, Metrics.o_mape,
            ]

def load_interval(filepath,width,height):
    #reading from the file into a string
    with open(filepath, "r") as f:
        f_string = f.read()
        #cleaning up the delimiters
        od_list = [ s.strip() for s in f_string.split(",")]
        #convering to an integer array
        od_arr = np.array(od_list)
        #ensuring that it is of the correct size
        assert len(od_arr) == width * height * height * width
        #reshaping into the format expected by the demand model
        od_input_size = (width*height, height, width)
        od_arr = np.reshape(od_arr,od_input_size)
        return od_arr

def save_interval(od_arr,filepath):
    #flattening into a 1D array
    od_arr_flattened = od_arr.flatten()
    #converting to a string with the same format as that expected by Jargo
    od_arr_string = np.array2string(od_arr_flattened, separator=", ")[1:-1]
    #writing to the file
    with open(filepath, "w") as f:
        f.write(od_arr_string)

#function to convert between OD data and TF-compatible (normalized) format
def od2tf(arr_od,odmax):
    arr = arr_od.astype(np.float64)
    arr = arr * 2
    arr = arr / odmax
    arr = arr - 1
    return arr

def tf2od(arr_tf,odmax):
    arr = arr_tf + 1
    arr = arr * odmax
    arr = arr / 2
    arr = arr.astype(int)
    return arr

#function to load the TF model and run it on the data
def run_model(od_in,model_path, model_param_dict):
    with tf.compat.v1.Session() as sess:
        loss = DEMODEL.get_loss()
        optimizer = Adam(lr=0.001)
        model = DEMODEL.build_model(**model_param_dict)
        model.compile(loss=loss, optimizer=optimizer, metrics=metrics)
        model.load_weights(model_path)
        Y_pred = model.predict(od_in)[0]
        sess.close()
    
    return Y_pred

#sample command
# ./predict_mock.py --in1 "./test_data/interval_1.npy" --in2 "./test_data/interval_2.npy" --in3 "./test_data/interval_3.npy" --in4 "./test_data/interval_4.npy" --in5 "./test_data/interval_4.npy" --out_raw "./test_data/pred_raw.npy"
if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--in1', required=True, type=str, help='path to the .npy file for interval 1')
    p.add_argument('--in2', required=True, type=str, help='path to the .npy file for interval 2')
    p.add_argument('--in3', required=True, type=str, help='path to the .npy file for interval 3')
    p.add_argument('--in4', required=True, type=str, help='path to the .npy file for interval 4')
    p.add_argument('--in5', required=True, type=str, help='path to the .npy file for interval 5')

    p.add_argument('--out_raw', required=True, type=str, help='path where to store the .npy file with raw predictions')

    p.add_argument('--model_file', required=True, type=str, help='path to the .h5 file with the trained model')
    p.add_argument('--model_width', required=False, type=int, help='width of the grid used by the model', default=5)
    p.add_argument('--model_height', required=False, type=int, help='height of the grid used by the model', default=20)
    p.add_argument('--model_odmax', required=False, type=int, help='normalization factor to use for the arrays (max demand)', default=287)

    args = p.parse_args()

    # model parameters
    model_para = {
        "timestep": 5,
        "map_height": args.model_height,
        "map_width": args.model_width,
        "weather_dim": 29,
        "meta_dim": 8,
    }

    #loading each of the intervals and combining them into a single array
    od_in_list = [ load_interval(in_path, args.model_width, args.model_height)
                for in_path in
                [args.in1, args.in2, args.in3, args.in4, args.in5]]
    od_in_arr = np.array([ od2tf(a, args.model_odmax) for a in od_in_list ])
    
    # "X" corresponds to the model input
    X_od = np.expand_dims(od_in_arr,axis=0)
    X_w = np.zeros((1, model_para['timestep'], model_para['weather_dim']))
    X_m = np.zeros((1, model_para['timestep'], model_para['meta_dim']))
    X = [X_od, X_w, X_m]

    # run inference
    Y_pred_tf = run_model(X,args.model_file, model_para)
    
    # convert from TF predictions to raw numbers of requests
    Y_pred_od = tf2od(Y_pred_tf, args.model_odmax)

    save_interval(Y_pred_od, args.out_raw)
